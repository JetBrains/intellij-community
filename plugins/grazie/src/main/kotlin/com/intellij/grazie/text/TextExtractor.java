package com.intellij.grazie.text;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.diagnostic.PluginException;
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy;
import com.intellij.grazie.ide.language.LanguageGrammarChecking;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.intellij.util.containers.ContainerUtil.createConcurrentWeakKeyWeakValueMap;

/**
 * An extension specifying how to extract natural language text from PSI for specific programming languages.
 */
public abstract class TextExtractor {
  private static final Logger LOG = Logger.getInstance(TextExtractor.class);
  @VisibleForTesting
  @ApiStatus.Internal
  public static final LanguageExtension<TextExtractor> EP = new LanguageExtension<>("com.intellij.grazie.textExtractor");
  private static final Key<CachedValue<Cache>> COMMON_PARENT_CACHE = Key.create("TextExtractor common parent cache");
  private static final Key<CachedValue<AtomicReference<List<TextContent>>>> QUERY_CACHE = Key.create("TextExtractor query cache");
  private static final Key<Boolean> IGNORED = Key.create("TextExtractor ignored");
  private static final Key<CachedValue<Map<TextContent, TextContent>>> CONTENT_INTERNER = Key.create("TextExtractor interner");
  private static final Pattern NOINSPECTION_SUPPRESSION = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP);
  private static final Pattern PROPERTY_SUPPRESSION = Pattern.compile("\\s*suppress inspection \"" + LocalInspectionTool.VALID_ID_PATTERN + "\"");
  private static final Pattern LICENSE_PATTERN = Pattern.compile("(?i)License.*?(as is|MIT|GNU|GPL|Apache|BSD)", Pattern.DOTALL);

  /**
   * Extract text from the given PSI element, if possible.
   * The returned text is most often fully embedded in {@code element},
   * but it may also include other PSI elements (e.g., adjacent comments).
   * In the latter case, this extension should return an equal {@link TextContent} for every one of those adjacent elements.
   * <p>
   * Typical usage:
   *
   * <pre><code class="java">TextContentBuilder.FromPsi.build(element, textDomain)</code></pre>
   *
   * Implementation guidance:
   * <p>
   * To maximize performance, guard against unnecessary (and sometimes quite expensive) operations by checking that
   * the requested textDomain is contained in allowedDomains before extracting.
   *
   * <pre><code class="java">
   * if (shouldExtractTextContent(root) && allowedDomains.contains(textDomain)) {
   *   // some other potentially performance-intensive operations
   *   return TextContentBuilder.FromPsi.build(root, textDomain)
   * }
   * </code></pre>
   *
   * See concrete implementations (e.g., in ChatInputTextExtractor, JsonTextExtractor, GoTextExtractor, etc.) for
   * examples.

   * @param allowedDomains the set of the text domains that are expected by the caller.
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
   * @return text contents intersecting the given PSI element with the domains from the allowed set.
   * <p>
   * Same as {@link #findTextsAt}, but the extensions are queried only for the given {@code psi}. The results are cached and reused.
   *
   * @deprecated {@link #findTextsAt} or {@link #findUniqueTextsAt} should be used instead.
   */
  @SuppressWarnings("unused")
  @Deprecated(forRemoval = true)
  public static @NotNull List<TextContent> findTextsExactlyAt(@NotNull PsiElement psi, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    PsiFile file = psi.getContainingFile();
    return ContainerUtil.filter(
      obtainContents(file, psi),
      c -> Boolean.FALSE.equals(c.getUserData(IGNORED)) && allowedDomains.contains(c.getDomain())
    );
  }

  /**
   * @return text contents intersecting the given PSI element with the domains from the allowed set.
   * The extensions are queried for the given {@code psi} and its parents, the results are cached and reused.
   */
  public static @Unmodifiable @NotNull List<TextContent> findTextsAt(@NotNull PsiElement psi, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    TextRange psiRange = psi.getTextRange();
    PsiFile file = null;
    for (PsiElement each = psi; each != null; each = each.getParent()) {
      CachedValue<Cache> cache = each.getUserData(COMMON_PARENT_CACHE);
      if (cache != null) {
        List<TextContent> textsInRange = ContainerUtil.filter(
          cache.getValue().getCached(),
          c -> c.intersectsRange(psiRange)
        );
        if (!textsInRange.isEmpty()) {
          return ContainerUtil.filter(
            textsInRange,
            c -> allowedDomains.contains(c.getDomain()) && Boolean.FALSE.equals(c.getUserData(IGNORED))
          );
        }
      }
      if (each instanceof PsiFile) {
        file = (PsiFile)each;
        break;
      }
    }

    for (PsiElement each = psi; each != null && each != file; each = each.getParent()) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();

      List<TextContent> contents = obtainContents(file != null ? file : psi.getContainingFile(), each);
      if (stamp.mayCacheNow() && !contents.isEmpty()) {
        StreamEx.of(contents)
          .groupingBy(TextContent::getCommonParent)
          .forEach((commonParent, group) -> obtainCommonParentCache(commonParent).register(group));
      }

      if (!contents.isEmpty()) {
        return ContainerUtil.filter(
          contents,
          c -> Boolean.FALSE.equals(c.getUserData(IGNORED)) && allowedDomains.contains(c.getDomain()) && c.intersectsRange(psiRange)
        );
      }
    }

    return Collections.emptyList();
  }

  private static AtomicReference<List<TextContent>> obtainQueryCache(PsiElement psi) {
    return obtainCache(psi, QUERY_CACHE, () -> new AtomicReference<>(null));
  }

  private static Cache obtainCommonParentCache(PsiElement psi) {
    return obtainCache(psi, COMMON_PARENT_CACHE, () -> new Cache());
  }

  private static <T> T obtainCache(PsiElement psi, Key<CachedValue<T>> key, Supplier<T> supplier) {
    var provider = TextContentModificationTrackerProvider.EP_NAME.forLanguage(psi.getLanguage());
    var providedTracker = provider != null ? provider.getModificationTracker(psi) : null;
    var tracker = providedTracker != null ? providedTracker : PsiModificationTracker.MODIFICATION_COUNT;

    CachedValue<T> cache = CachedValuesManager.getManager(psi.getProject())
      .createCachedValue(() -> CachedValueProvider.Result.create(supplier.get(), tracker));
    cache = ((UserDataHolderEx)psi).putUserDataIfAbsent(key, cache);
    return cache.getValue();
  }

  private static Map<TextContent, TextContent> obtainInterner(PsiFile file) {
    CachedValue<Map<TextContent, TextContent>> cache = CachedValuesManager.getManager(file.getProject())
      .createCachedValue(() -> CachedValueProvider.Result.create(createConcurrentWeakKeyWeakValueMap(), file));
    cache = ((UserDataHolderEx)file).putUserDataIfAbsent(CONTENT_INTERNER, cache);
    return cache.getValue();
  }

  private static List<TextContent> obtainContents(PsiFile file, PsiElement psi) {
    CachedValue<AtomicReference<List<TextContent>>> cv = psi.getUserData(QUERY_CACHE);
    List<TextContent> cachedContent = cv != null ? cv.getValue().get() : null;
    if (cachedContent != null) return cachedContent;

    RecursionGuard.StackStamp stamp = RecursionManager.markStack();

    Language psiLanguage = psi.getLanguage();
    List<TextContent> contents = doExtract(psi, psiLanguage);
    Language fileLanguage = file.getLanguage();
    if (contents.isEmpty() && fileLanguage != psiLanguage) {
      contents = doExtract(psi, fileLanguage);
    }
    if (contents.isEmpty()) return List.of();

    // deduplicate equal contents created by different threads to avoid O(token_count) 'equals' checks later on
    var interner = obtainInterner(file);
    contents = ContainerUtil.map(contents, content -> interner.computeIfAbsent(content, __ -> content));

    for (TextContent content : contents) {
      if (content.getUserData(IGNORED) != null) continue;
      content.putUserData(IGNORED, shouldIgnore(content));
    }

    if (stamp.mayCacheNow()) {
      obtainQueryCache(psi).compareAndSet(null, contents);
      cacheOnSiblings(psi, contents);
    }
    return contents;
  }

  private static void cacheOnSiblings(PsiElement psi, List<TextContent> contents) {
    contents.forEach(content -> {
      int startOffset = content.textOffsetToFile(0);
      int endOffset = content.textOffsetToFile(content.length());
      TextRange psiRangeInFile = psi.getTextRange();
      if (psiRangeInFile.getStartOffset() > startOffset || psiRangeInFile.getEndOffset() < endOffset) {
        for (ASTNode child : psi.getParent().getNode().getChildren(null)) {
          PsiElement sibling = child.getPsi();
          List<TextContent> intersectingContents = contents.stream()
            .filter(it -> it.intersectsRange(sibling.getTextRange()))
            .toList();
          if (!intersectingContents.isEmpty()) {
            obtainQueryCache(sibling).compareAndSet(null, intersectingContents);
          }
        }
      }
    });
  }

  private static class Cache {
    private final Set<TextContent> foundContents = new LinkedHashSet<>();

    synchronized void register(List<TextContent> contents) {
      foundContents.addAll(contents);
    }

    synchronized List<TextContent> getCached() {
      return new ArrayList<>(foundContents);
    }
  }

  private static boolean shouldIgnore(TextContent content) {
    return isSuppressionComment(content) ||
           isCopyrightComment(content) ||
           hasIntersectingInjection(content, content.getContainingFile());
  }

  private static boolean isCopyrightComment(TextContent content) {
    return (content.getDomain() == TextContent.TextDomain.COMMENTS || content.getDomain() == TextContent.TextDomain.DOCUMENTATION)
           && StringUtil.containsIgnoreCase(content.toString(), "Copyright")
           && (isAtFileStart(content) || looksLikeLicense(content));
  }

  private static boolean looksLikeLicense(TextContent content) {
    return LICENSE_PATTERN.matcher(content).find();
  }

  private static boolean isAtFileStart(TextContent content) {
    PsiFile file = content.getContainingFile();
    int textStart = content.textOffsetToFile(0);
    return file.getViewProvider().getContents().subSequence(0, textStart).chars().noneMatch(Character::isLetterOrDigit);
  }

  private static boolean isSuppressionComment(TextContent content) {
    return content.getDomain() == TextContent.TextDomain.COMMENTS &&
           (NOINSPECTION_SUPPRESSION.matcher(content).lookingAt() || PROPERTY_SUPPRESSION.matcher(content).lookingAt());
  }

  /**
   * The same as {@link #findTextsAt}, but each text content is returned only once even if it covers several PSI elements
   * (one of those elements is chosen as an anchor).
   * That's useful if you iterate over PSI elements and want to process each of their contents just once
   * (e.g. during highlighting).
   */
  public static @Unmodifiable @NotNull List<TextContent> findUniqueTextsAt(@NotNull PsiElement psi, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (psi.getFirstChild() != null) return Collections.emptyList();

    TextRange psiRange = psi.getTextRange();
    return ContainerUtil.filter(findTextsAt(psi, allowedDomains), c -> psiRange.contains(c.textOffsetToFile(0)));
  }


  /**
   * Extract all text contents from a file view provider that match the specified domains.
   * Traverses through all PSI elements in all root files of the view provider and collects matching text contents.
   */
  public static Set<TextContent> findAllTextContents(FileViewProvider vp, Set<TextContent.TextDomain> domains) {
    Set<TextContent> allContents = new HashSet<>();
    for (PsiFile root : vp.getAllFiles()) {
      for (PsiElement element : SyntaxTraverser.psiTraverser(root)) {
        if (element instanceof PsiWhiteSpace) continue;
        allContents.addAll(findTextsAt(element, domains));
      }
    }
    return allContents;
  }

  private static boolean hasIntersectingInjection(TextContent content, PsiFile file) {
    return InjectedLanguageManager.getInstance(file.getProject()).findInjectedElementAt(file, content.textOffsetToFile(0)) != null;
  }

  @SuppressWarnings("deprecation")
  private static @NotNull List<TextContent> doExtract(@NotNull PsiElement anyRoot, @NotNull Language language) {
    TextExtractor extractor = EP.forLanguage(language);
    if (extractor != null) {
      List<TextContent> contents = extractor.buildTextContents(anyRoot, TextContent.TextDomain.ALL);
      for (TextContent content : contents) {
        if (!content.getCommonParent().getTextRange().contains(content.textRangeToFile(TextRange.from(0, content.length())))) {
          if (!(extractor instanceof EventuallyConsistentTextExtractor)) {
            PluginException.logPluginError(LOG, "Inconsistent text content", null, extractor.getClass());
          }
          return List.of();
        }
      }
      return contents;
    }

    // legacy extraction
    for (GrammarCheckingStrategy strategy : LanguageGrammarChecking.INSTANCE.allForLanguage(language)) {
      if (strategy.isMyContextRoot(anyRoot)) {
        GrammarCheckingStrategy.TextDomain oldDomain = strategy.getContextRootTextDomain(anyRoot);
        TextContent.TextDomain domain = StrategyTextExtractor.convertDomain(oldDomain);
        if (domain != null) {
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
    ExtensionPoint<LanguageExtensionPoint<TextExtractor>> ep =
      ApplicationManager.getApplication().getExtensionArea().getExtensionPoint(EP.getName());
    for (var point : ep.getExtensionList()) {
      ContainerUtil.addIfNotNull(result, Language.findLanguageByID(point.language));
    }
    //noinspection deprecation
    for (var point : LanguageGrammarChecking.EP_NAME.getExtensionList()) {
      ContainerUtil.addIfNotNull(result, Language.findLanguageByID(point.language));
    }
    return result;
  }
  
  @TestOnly
  public @NotNull List<TextContent> buildTextContentsTestAccessor(@NotNull PsiElement element, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    return buildTextContents(element, allowedDomains);
  }
}
