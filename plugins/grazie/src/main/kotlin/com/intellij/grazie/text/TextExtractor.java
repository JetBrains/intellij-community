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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An extension specifying how to extract natural language text from PSI for specific programming languages.
 */
public abstract class TextExtractor {
  private static final LanguageExtension<TextExtractor> EP = new LanguageExtension<>("com.intellij.grazie.textExtractor");
  private static final Key<CachedValue<Set<TextContent>>> CACHE = Key.create("TextExtractor cache");
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
   */
  protected abstract @Nullable TextContent buildTextContent(@NotNull PsiElement element, @NotNull Set<TextContent.TextDomain> allowedDomains);

  /**
   * @return a text content intersecting the given PSI element with the domain from the allowed set.
   * The extensions are queried for the given {@code psi} and its parents, the results are cached and reused.
   */
  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  @Nullable
  public static TextContent findTextAt(@NotNull PsiElement psi, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    JBIterable<PsiElement> hierarchy = SyntaxTraverser.psiApi().parents(psi);
    for (PsiElement each : hierarchy) {
      CachedValue<Set<TextContent>> cache = each.getUserData(CACHE);
      if (cache != null) {
        synchronized (cache) {
          for (TextContent content : cache.getValue()) {
            if (allowedDomains.contains(content.getDomain()) && isSuitable(content, psi)) {
              return content;
            }
          }
        }
      }
    }

    Language fileLanguage = psi.getContainingFile().getLanguage();

    for (PsiElement each : hierarchy) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();

      Language psiLanguage = each.getLanguage();
      TextContent content = doExtract(each, allowedDomains, psiLanguage);
      if (content == null && fileLanguage != psiLanguage) {
        content = doExtract(each, allowedDomains, fileLanguage);
      }
      if (content != null && stamp.mayCacheNow()) {
        PsiElement parent = content.getCommonParent();
        CachedValue<Set<TextContent>> cache = CachedValuesManager.getManager(parent.getProject()).createCachedValue(
          () -> CachedValueProvider.Result.create(new LinkedHashSet<>(), PsiModificationTracker.MODIFICATION_COUNT));
        cache = ((UserDataHolderEx) parent).putUserDataIfAbsent(CACHE, cache);
        synchronized (cache) {
          cache.getValue().add(content);
        }
      }

      if (content != null) {
        return isSuitable(content, psi) ? content : null;
      }
    }

    return null;
  }

  private static boolean isSuitable(TextContent content, PsiElement psi) {
    return content.intersectsRange(psi.getTextRange()) &&
           !hasIntersectingInjection(content, psi.getContainingFile()) &&
           !isSuppressionComment(content);
  }

  private static boolean isSuppressionComment(TextContent content) {
    return content.getDomain() == TextContent.TextDomain.COMMENTS && SUPPRESSION.matcher(content).matches();
  }

  /**
   * The same as {@link #findTextAt}, but each text content is returned only once even if it covers several PSI elements
   * (one of those elements is chosen as an anchor).
   * That's useful if you iterate over PSI elements and want to process each of their contents just once
   * (e.g. during highlighting).
   */
  public static @Nullable TextContent findUniqueTextAt(@NotNull PsiElement psi, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (psi.getFirstChild() != null) return null;

    var extracted = findTextAt(psi, allowedDomains);
    return extracted != null && psi.getTextRange().contains(extracted.textOffsetToFile(0)) ? extracted : null;
  }

  private static boolean hasIntersectingInjection(TextContent content, PsiFile file) {
    return InjectedLanguageManager.getInstance(file.getProject()).findInjectedElementAt(file, content.textOffsetToFile(0)) != null;
  }

  @SuppressWarnings("deprecation")
  private static TextContent doExtract(@NotNull PsiElement anyRoot, @NotNull Set<TextContent.TextDomain> allowedDomains, @NotNull Language language) {
    TextExtractor extractor = EP.forLanguage(language);
    if (extractor != null) {
      TextContent roots = extractor.buildTextContent(anyRoot, allowedDomains);
      return roots != null && allowedDomains.contains(roots.getDomain()) ? roots : null;
    }

    // legacy extraction
    for (GrammarCheckingStrategy strategy : LanguageGrammarChecking.INSTANCE.allForLanguage(language)) {
      if (strategy.isMyContextRoot(anyRoot)) {
        GrammarCheckingStrategy.TextDomain oldDomain = strategy.getContextRootTextDomain(anyRoot);
        TextContent.TextDomain domain = StrategyTextExtractor.convertDomain(oldDomain);
        if (domain != null && allowedDomains.contains(domain)) {
          return new StrategyTextExtractor(strategy).extractText(strategy.getRootsChain(anyRoot));
        }
      }
    }

    return null;
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
