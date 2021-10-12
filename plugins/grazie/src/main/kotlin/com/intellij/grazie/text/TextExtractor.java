package com.intellij.grazie.text;

import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy;
import com.intellij.grazie.ide.language.LanguageGrammarChecking;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * An extension specifying how to extract natural language text from PSI for specific programming languages.
 */
public abstract class TextExtractor {
  private static final LanguageExtension<TextExtractor> EP = new LanguageExtension<>("com.intellij.grazie.textExtractor");
  private static final Key<CachedValue<Cache>> CACHE = Key.create("TextExtractor cache");
  private static final Pattern SUPPRESSION = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP);

  /**
   * Extract text from the given PSI element, if possible.
   * The returned text is most often fully embedded in {@code element},
   * but it may also include other PSI elements (e.g. adjacent comments).
   * In the latter case, this extension should return an equal {@link TextContent} for every one of those adjacent elements.
   * @param allowedDomains the set of the text domains that are expected by the caller.
   *                       The extension may check this set before doing unnecessary expensive PSI traversal
   *                       to improve the performance,
   *                       but it's not necessary.
   * @see TextContentBuilder
   * @see #buildTextContents
   */
  protected @Nullable TextContent buildTextContent(@NotNull PsiElement element, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    throw new UnsupportedOperationException("Please implement either buildTextContent or buildTextContents");
  }

  /**
   * Same as {@link #buildTextContent}, but may return several texts intersecting (most often embedded into) the given PSI element.
   * If any of those contents include other PSI elements (e.g. adjacent comments),
   * this extension should return equal {@code List<TextContent>} for all such "other" PSI elements.
   */
  protected @NotNull List<TextContent> buildTextContents(@NotNull PsiElement element, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    return ContainerUtil.createMaybeSingletonList(buildTextContent(element, allowedDomains));
  }

  /** Find a text content compatible with the given domain set intersecting with the given offset in a file. */
  public static @Nullable TextContent findTextAt(@NotNull PsiFile file, int offset, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    PsiElement leaf = file.findElementAt(offset);
    TextContent result = leaf == null ? null :
                         ContainerUtil.find(findTextsAt(leaf, allowedDomains), c -> c.fileOffsetToText(offset) != null);
    if (result != null) {
      return result;
    }
    if (leaf == null || offset == leaf.getTextRange().getStartOffset()) {
      leaf = file.findElementAt(offset - 1);
      if (leaf != null) {
        return ContainerUtil.find(findTextsAt(leaf, allowedDomains), c -> c.fileOffsetToText(offset) != null);
      }
    }
    return null;
  }

  /**
   * @deprecated use {@link #findTextsAt}
   * @return the first of {@link #findTextsAt} or null if it's empty
   */
  @Deprecated
  @Nullable
  public static TextContent findTextAt(@NotNull PsiElement psi, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    return ContainerUtil.getFirstItem(findTextsAt(psi, allowedDomains));
  }

  /**
   * @return text contents intersecting the given PSI element with the domains from the allowed set.
   * The extensions are queried for the given {@code psi} and its parents, the results are cached and reused.
   */
  public static @NotNull List<TextContent> findTextsAt(@NotNull PsiElement psi, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    JBIterable<PsiElement> hierarchy = SyntaxTraverser.psiApi().parents(psi);
    for (PsiElement each : hierarchy) {
      CachedValue<Cache> cache = each.getUserData(CACHE);
      if (cache != null) {
        List<TextContent> cached = ContainerUtil.filter(cache.getValue().getCached(allowedDomains), c ->
          isSuitable(c, psi) && allowedDomains.contains(c.getDomain()));
        if (!cached.isEmpty()) {
          return cached;
        }
      }
    }

    Language fileLanguage = psi.getContainingFile().getLanguage();

    for (PsiElement each : hierarchy) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();

      Language psiLanguage = each.getLanguage();
      List<TextContent> contents = doExtract(each, allowedDomains, psiLanguage);
      if (contents.isEmpty() && fileLanguage != psiLanguage) {
        contents = doExtract(each, allowedDomains, fileLanguage);
      }
      if (stamp.mayCacheNow()) {
        for (TextContent content : contents) {
          PsiElement parent = content.getCommonParent();
          CachedValue<Cache> cache = CachedValuesManager.getManager(parent.getProject()).createCachedValue(
            () -> CachedValueProvider.Result.create(new Cache(), PsiModificationTracker.MODIFICATION_COUNT));
          cache = ((UserDataHolderEx) parent).putUserDataIfAbsent(CACHE, cache);
          cache.getValue().register(allowedDomains, content);
        }
      }

      if (!contents.isEmpty()) {
        return ContainerUtil.filter(contents, c -> isSuitable(c, psi) && allowedDomains.contains(c.getDomain()));
      }
    }

    return Collections.emptyList();
  }

  private static class Cache {
    final EnumSet<TextContent.TextDomain> checkedDomains = EnumSet.noneOf(TextContent.TextDomain.class);
    final LinkedHashSet<TextContent> foundContents = new LinkedHashSet<>();

    synchronized void register(Set<TextContent.TextDomain> allowedDomains, TextContent content) {
      checkedDomains.addAll(allowedDomains);
      foundContents.add(content);
    }

    synchronized Collection<TextContent> getCached(Set<TextContent.TextDomain> allowedDomains) {
      return checkedDomains.containsAll(allowedDomains) ? foundContents : Collections.emptyList();
    }
  }

  private static boolean isSuitable(TextContent content, PsiElement psi) {
    return content.intersectsRange(psi.getTextRange()) &&
           !hasIntersectingInjection(content, psi.getContainingFile()) &&
           !isSuppressionComment(content) &&
           !isCopyrightComment(content);
  }

  private static boolean isCopyrightComment(TextContent content) {
    return (content.getDomain() == TextContent.TextDomain.COMMENTS || content.getDomain() == TextContent.TextDomain.DOCUMENTATION) &&
           StringUtil.containsIgnoreCase(content.toString(), "Copyright") &&
           isAtFileStart(content);
  }

  private static boolean isAtFileStart(TextContent content) {
    PsiFile file = content.getContainingFile();
    int textStart = content.textOffsetToFile(0);
    return file.getViewProvider().getContents().subSequence(0, textStart).chars().noneMatch(Character::isLetterOrDigit);
  }

  private static boolean isSuppressionComment(TextContent content) {
    return content.getDomain() == TextContent.TextDomain.COMMENTS && SUPPRESSION.matcher(content).matches();
  }

  /**
   * @deprecated use {@link #findUniqueTextsAt}
   * @return the first of {@link #findUniqueTextsAt} or null if it's empty
   */
  @Deprecated
  public static @Nullable TextContent findUniqueTextAt(@NotNull PsiElement psi, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    return ContainerUtil.getFirstItem(findUniqueTextsAt(psi, allowedDomains));
  }

  /**
   * The same as {@link #findTextsAt}, but each text content is returned only once even if it covers several PSI elements
   * (one of those elements is chosen as an anchor).
   * That's useful if you iterate over PSI elements and want to process each of their contents just once
   * (e.g. during highlighting).
   */
  public static @NotNull List<TextContent> findUniqueTextsAt(@NotNull PsiElement psi, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (psi.getFirstChild() != null) return Collections.emptyList();

    TextRange psiRange = psi.getTextRange();
    return ContainerUtil.filter(findTextsAt(psi, allowedDomains), c -> psiRange.contains(c.textOffsetToFile(0)));
  }

  private static boolean hasIntersectingInjection(TextContent content, PsiFile file) {
    return InjectedLanguageManager.getInstance(file.getProject()).findInjectedElementAt(file, content.textOffsetToFile(0)) != null;
  }

  @SuppressWarnings("deprecation")
  private static @NotNull List<TextContent> doExtract(@NotNull PsiElement anyRoot,
                                                      @NotNull Set<TextContent.TextDomain> allowedDomains,
                                                      @NotNull Language language) {
    TextExtractor extractor = EP.forLanguage(language);
    if (extractor != null) {
      return extractor.buildTextContents(anyRoot, allowedDomains);
    }

    // legacy extraction
    for (GrammarCheckingStrategy strategy : LanguageGrammarChecking.INSTANCE.allForLanguage(language)) {
      if (strategy.isMyContextRoot(anyRoot)) {
        GrammarCheckingStrategy.TextDomain oldDomain = strategy.getContextRootTextDomain(anyRoot);
        TextContent.TextDomain domain = StrategyTextExtractor.convertDomain(oldDomain);
        if (domain != null && allowedDomains.contains(domain)) {
          return ContainerUtil.createMaybeSingletonList(new StrategyTextExtractor(strategy).extractText(strategy.getRootsChain(anyRoot)));
        }
      }
    }

    return Collections.emptyList();
  }

  /**
   * @return all languages for which a text extractor is explicitly registered
   */
  public static Set<Language> getSupportedLanguages() {
    Set<Language> result = new HashSet<>();
    ExtensionPoint<LanguageExtensionPoint<TextExtractor>> ep = ApplicationManager.getApplication().getExtensionArea().getExtensionPoint(EP.getName());
    for (var point : ep.getExtensionList()) {
      ContainerUtil.addIfNotNull(result, Language.findLanguageByID(point.language));
    }
    //noinspection deprecation
    for (var point : LanguageGrammarChecking.EP_NAME.getExtensionList()) {
      ContainerUtil.addIfNotNull(result, Language.findLanguageByID(point.language));
    }
    return result;
  }
}
