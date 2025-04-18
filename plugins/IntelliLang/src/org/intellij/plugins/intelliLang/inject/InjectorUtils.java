// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringSearcher;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Gregory.Shrago
 */
public final class InjectorUtils {
  public static final Comparator<TextRange> RANGE_COMPARATOR = (o1, o2) -> {
    if (o1.intersects(o2)) return 0;
    return o1.getStartOffset() - o2.getStartOffset();
  };

  private InjectorUtils() {
  }

  public static @Nullable Language getLanguage(@NotNull BaseInjection injection) {
    return getLanguageByString(injection.getInjectedLanguageId());
  }

  public static @Nullable Language getLanguageByString(@NotNull String languageId) {
    Language language = InjectedLanguage.findLanguageById(languageId);
    if (language != null) return language;
    ReferenceInjector injector = ReferenceInjector.findById(languageId);
    if (injector != null) return injector.toLanguage();
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType fileType = fileTypeManager.getFileTypeByExtension(languageId);
    if (fileType instanceof LanguageFileType) {
      return ((LanguageFileType)fileType).getLanguage();
    }

    LightVirtualFile virtualFileNamedAsLanguageId = new LightVirtualFile(languageId);
    LightVirtualFile virtualFileWithLanguageIdAsExtension = new LightVirtualFile("textmate." + languageId);
    for (FileType registeredFileType : fileTypeManager.getRegisteredFileTypes()) {
      if (registeredFileType instanceof FileTypeIdentifiableByVirtualFile &&
          registeredFileType instanceof LanguageFileType &&
          (((FileTypeIdentifiableByVirtualFile)registeredFileType).isMyFileType(virtualFileNamedAsLanguageId) ||
           ((FileTypeIdentifiableByVirtualFile)registeredFileType).isMyFileType(virtualFileWithLanguageIdAsExtension))) {
        return ((LanguageFileType)registeredFileType).getLanguage();
      }
    }
    return null;
  }

  public static boolean registerInjectionSimple(@NotNull PsiLanguageInjectionHost host,
                                                @NotNull BaseInjection injection,
                                                @Nullable LanguageInjectionSupport support,
                                                @NotNull MultiHostRegistrar registrar) {
    Language language = getLanguage(injection);
    if (language == null) return false;

    InjectedLanguage injectedLanguage =
      InjectedLanguage.create(injection.getInjectedLanguageId(), injection.getPrefix(), injection.getSuffix(), false);

    List<TextRange> ranges = injection.getInjectedArea(host);
    List<InjectionInfo> list = new ArrayList<>(ranges.size());

    for (TextRange range : ranges) {
      list.add(new InjectionInfo(host, injectedLanguage, range));
    }
    registerInjection(language, host.getContainingFile(), list, registrar, it -> {
      if (support != null) {
        registerSupport(it, support, true);
      }
    });
    return !ranges.isEmpty();
  }

  /**
   * Record that represents a single injection
   * @param host the element that hosts the injection
   * @param language language of the injected content
   * @param range range of the injection within the host
   */
  public record InjectionInfo(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguage language, @NotNull TextRange range) {
  }

  /**
   * @deprecated use {@link #registerInjection(Language, PsiFile, List, MultiHostRegistrar)}
   */
  @Deprecated(forRemoval = true)
  public static void registerInjection(@Nullable Language language,
                                       @NotNull List<? extends Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list,
                                       @NotNull PsiFile containingFile,
                                       @NotNull MultiHostRegistrar registrar) {
    registerInjection(language, containingFile,
                      ContainerUtil.map(list, trinity -> new InjectionInfo(trinity.first, trinity.second, trinity.third)), registrar);
  }
  public static void registerInjection(@Nullable Language language,
                                       @NotNull PsiFile containingFile,
                                       @NotNull List<InjectionInfo> list,
                                       @NotNull MultiHostRegistrar registrar) {
    registerInjection(language, containingFile, list, registrar, null);
  }


  public static void registerInjection(@Nullable Language language,
                                       @NotNull PsiFile containingFile,
                                       @NotNull List<InjectionInfo> list,
                                       @NotNull MultiHostRegistrar registrar,
                                       @Nullable Consumer<MultiHostRegistrar> customizeInjection) {
    // if language isn't injected when length == 0, subsequent edits will not cause the language to be injected as well.
    // Maybe IDEA core is caching a bit too aggressively here?
    if (language == null/* && (pair.second.getLength() > 0*/) {
      return;
    }
    ParserDefinition parser = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    ReferenceInjector injector = ReferenceInjector.findById(language.getID());
    if (parser == null && injector != null) {
      for (InjectionInfo trinity : list) {
        String prefix = trinity.language().getPrefix();
        String suffix = trinity.language().getSuffix();
        PsiLanguageInjectionHost host = trinity.host();
        TextRange textRange = trinity.range();
        InjectedLanguageUtil.injectReference(registrar, language, prefix, suffix, host, textRange);
        return;
      }
      return;
    }
    boolean injectionStarted = false;
    for (InjectionInfo t : list) {
      PsiLanguageInjectionHost host = t.host();
      if (host.getContainingFile() != containingFile || !host.isValidHost()) continue;

      TextRange textRange = t.range();
      InjectedLanguage injectedLanguage = t.language();

      if (!injectionStarted) {
        // TextMate language requires file extension
        if (!StringUtil.equalsIgnoreCase(language.getID(), t.language().getID())) {
          registrar.startInjecting(language, StringUtil.toLowerCase(t.language().getID()));
        }
        else {
          registrar.startInjecting(language);
        }
        injectionStarted = true;
      }
      registrar.addPlace(injectedLanguage.getPrefix(), injectedLanguage.getSuffix(), host, textRange);
    }
    if (customizeInjection != null) {
      customizeInjection.accept(registrar);
    }
    if (injectionStarted) {
      registrar.doneInjecting();
    }
  }

  public static @NotNull @Unmodifiable Collection<String> getActiveInjectionSupportIds() {
    return ContainerUtil.map(LanguageInjectionSupport.EP_NAME.getExtensionList(), LanguageInjectionSupport::getId);
  }

  public static @NotNull Collection<LanguageInjectionSupport> getActiveInjectionSupports() {
    return LanguageInjectionSupport.EP_NAME.getExtensionList();
  }

  public static @Nullable LanguageInjectionSupport findInjectionSupport(@NotNull String id) {
    if (TemporaryPlacesRegistry.SUPPORT_ID.equals(id)) return new TemporaryLanguageInjectionSupport();
    for (LanguageInjectionSupport support : LanguageInjectionSupport.EP_NAME.getExtensionList()) {
      if (id.equals(support.getId())) return support;
    }
    return null;
  }

  public static Class<?> @NotNull [] getPatternClasses(@NotNull String supportId) {
    final LanguageInjectionSupport support = findInjectionSupport(supportId);
    return support == null ? ArrayUtil.EMPTY_CLASS_ARRAY : support.getPatternClasses();
  }

  public static @NotNull LanguageInjectionSupport findNotNullInjectionSupport(@NotNull String id) {
    LanguageInjectionSupport result = findInjectionSupport(id);
    if (result == null) {
      throw new IllegalStateException(id + " injector not found");
    }
    return result;
  }

  public static @NotNull StringBuilder appendStringPattern(@NotNull StringBuilder sb, @NotNull String prefix, @NotNull String text, @NotNull String suffix) {
    sb.append(prefix).append("string().");
    final String[] parts = text.split("[,|\\s]+");
    boolean useMatches = false;
    for (String part : parts) {
      if (isRegexp(part)) {
        useMatches = true;
        break;
      }
    }
    if (useMatches) {
      sb.append("matches(\"").append(text).append("\")");
    }
    else if (parts.length > 1) {
      sb.append("oneOf(");
      boolean first = true;
      for (String part : parts) {
        if (first) first = false;
        else sb.append(", ");
        sb.append("\"").append(part).append("\"");
      }
      sb.append(")");
    }
    else {
      sb.append("equalTo(\"").append(text).append("\")");
    }
    sb.append(suffix);
    return sb;
  }

  public static boolean isRegexp(@NotNull String s) {
    boolean hasReChars = false;
    for (int i = 0, len = s.length(); i < len; i++) {
      final char c = s.charAt(i);
      if (c == ' ' || c == '_' || c == '-' || Character.isLetterOrDigit(c)) continue;
      hasReChars = true;
      break;
    }
    if (hasReChars) {
      try {
        //noinspection ResultOfObjectAllocationIgnored
        new URL(s);
      }
      catch (MalformedURLException e) {
        return true;
      }
    }
    return false;
  }

  /**
   * @deprecated Use {@link #registerSupport(MultiHostRegistrar, LanguageInjectionSupport, boolean)} instead
   */
  @Deprecated
  public static void registerSupport(@NotNull LanguageInjectionSupport support,
                                     boolean settingsAvailable,
                                     @NotNull PsiElement element,
                                     @NotNull Language language) {
    putInjectedFileUserData(element, language, LanguageInjectionSupport.INJECTOR_SUPPORT, support);
    if (settingsAvailable) {
      putInjectedFileUserData(element, language, LanguageInjectionSupport.SETTINGS_EDITOR, support);
    }
  }

  public static void registerSupport(@NotNull MultiHostRegistrar registrar,
                                     @NotNull LanguageInjectionSupport support,
                                     boolean settingsAvailable) {
    registrar.putInjectedFileUserData(LanguageInjectionSupport.INJECTOR_SUPPORT, support);
    if (settingsAvailable) {
      registrar.putInjectedFileUserData(LanguageInjectionSupport.SETTINGS_EDITOR, support);
    }
  }

  /**
   * Does not work with multiple injections on the same host.
   *
   * @deprecated Use {@link MultiHostRegistrar#putInjectedFileUserData(Key, Object)} when registering the injection.
   */
  @Deprecated
  public static <T> void putInjectedFileUserData(@NotNull PsiElement element, @NotNull Language language, @NotNull Key<T> key, @Nullable T value) {
    InjectedLanguageUtil.putInjectedFileUserData(element, language, key, value);
  }

  @SuppressWarnings("UnusedParameters")
  public static Configuration getEditableInstance(@NotNull Project project) {
    return Configuration.getInstance();
  }

  public static boolean canBeRemoved(@NotNull BaseInjection injection) {
    if (injection.isEnabled()) return false;
    if (StringUtil.isNotEmpty(injection.getPrefix()) || StringUtil.isNotEmpty(injection.getSuffix())) return false;
    if (StringUtil.isNotEmpty(injection.getValuePattern())) return false;
    return true;
  }

  private static @Nullable CommentInjectionData findCommentInjectionData(@NotNull PsiElement context, @Nullable Ref<? super PsiElement> causeRef) {
    return findCommentInjectionData(context, true, causeRef);
  }

  public static @Nullable Pair.NonNull<PsiComment, CommentInjectionData> findClosestCommentInjectionData(@NotNull PsiElement context) {
    PsiFile file = context.getContainingFile();
    if (file == null) return null;
    TreeMap<TextRange, CommentInjectionData> map = getInjectionMap(file);
    if (map == null) return null;
    Map.Entry<TextRange, CommentInjectionData> entry = map.lowerEntry(context.getTextRange());
    if (entry == null) return null;
    PsiComment psiComment = PsiTreeUtil.findElementOfClassAtOffset(file, entry.getKey().getStartOffset(), PsiComment.class, false);
    if (psiComment == null) return null;
    return Pair.createNonNull(psiComment, entry.getValue());
  }

  public static @Nullable CommentInjectionData findCommentInjectionData(@NotNull PsiElement context, boolean treeElementsIncludeComment, @Nullable Ref<? super PsiElement> causeRef) {
    PsiElement target = CompletionUtil.getOriginalOrSelf(context);
    Pair.NonNull<PsiComment, CommentInjectionData> pair = findClosestCommentInjectionData(target);
    if (pair == null) return null;
    PsiComment psiComment = pair.first;
    CommentInjectionData injectionData = pair.second;
    TextRange r0 = psiComment.getTextRange();

    // calculate topmost siblings & heights
    PsiElement commonParent = PsiTreeUtil.findCommonParent(psiComment, target);
    if (commonParent == null) return null;
    PsiElement topmostElement = target;
    PsiElement parent = target;
    while (parent != null && (treeElementsIncludeComment ? parent : parent.getParent()) != commonParent) {
      topmostElement = parent;
      parent = parent.getParent();
    }

    // make sure comment is close enough and ...
    int off1 = r0.getEndOffset();
    int off2 = topmostElement.getTextRange().getStartOffset();
    if (off2 - off1 > 120) {
      return null;
    }
    if (off2 - off1 > 2) {
      // ... there's no non-empty valid host in between comment and topmostElement
      Supplier<PsiElement> producer = prevWalker(topmostElement, commonParent);
      PsiElement e;
      while ( (e = producer.get()) != null && e != psiComment) {
        if (e instanceof PsiLanguageInjectionHost &&
            ((PsiLanguageInjectionHost)e).isValidHost() &&
            !StringUtil.isEmptyOrSpaces(e.getText())) {
          return null;
        }
      }
    }
    if (causeRef != null) {
      causeRef.set(psiComment);
    }
    return injectionData;
  }

  public static @Nullable BaseInjection findCommentInjection(@NotNull PsiElement context,
                                                             @NotNull String supportId,
                                                             @Nullable Ref<? super PsiElement> causeRef) {
    CommentInjectionData data = findCommentInjectionData(context, causeRef);
    if (data == null) return null;
    BaseInjection injection = new BaseInjection(supportId);
    injection.setPrefix(data.getPrefix());
    injection.setSuffix(data.getSuffix());
    injection.setInjectedLanguageId(data.getInjectedLanguageId());
    injection.setDisplayName(data.getDisplayName());
    return injection;
  }

  private static @Nullable TreeMap<TextRange, CommentInjectionData> getInjectionMap(final @NotNull PsiFile file) {
    return CachedValuesManager.getCachedValue(file, () -> {
      TreeMap<TextRange, CommentInjectionData> map = calcInjections(file);
      return CachedValueProvider.Result.create(map.isEmpty() ? null : map, file);
    });
  }

  private static @NotNull TreeMap<TextRange,CommentInjectionData> calcInjections(@NotNull PsiFile file) {
    final TreeMap<TextRange, CommentInjectionData> injectionMap = new TreeMap<>(RANGE_COMPARATOR);

    StringSearcher searcher = new StringSearcher("language=", true, true, false);
    CharSequence contents = file.getViewProvider().getContents();
    final char[] contentsArray = CharArrayUtil.fromSequenceWithoutCopying(contents);

    int s0 = 0;
    int s1 = contents.length();
    for (int idx = searcher.scan(contents, contentsArray, s0, s1);
         idx != -1;
         idx = searcher.scan(contents, contentsArray, idx + 1, s1)) {
      PsiComment element = PsiTreeUtil.findElementOfClassAtOffset(file, idx, PsiComment.class, false);
      if (element != null) {
        String str = ElementManipulators.getValueText(element).trim();
        CommentInjectionData injection = str.startsWith("language=") ? new CommentInjectionData(decodeMap(str), str) : null;
        if (injection != null) {
          injectionMap.put(element.getTextRange(), injection);
        }
      }
    }
    return injectionMap;
  }

  private static final Pattern MAP_ENTRY_PATTERN = Pattern.compile("([\\S&&[^=]]+)=(\"(?:[^\"]|\\\\\")*\"|\\S*)");
  private static @NotNull Map<String, String> decodeMap(@NotNull CharSequence charSequence) {
    if (StringUtil.isEmpty(charSequence)) return Collections.emptyMap();
    final Matcher matcher = MAP_ENTRY_PATTERN.matcher(charSequence);
    final LinkedHashMap<String, String> map = new LinkedHashMap<>();
    while (matcher.find()) {
      map.put(StringUtil.unescapeStringCharacters(matcher.group(1)),
              StringUtil.unescapeStringCharacters(StringUtil.unquoteString(matcher.group(2))));
    }
    return map;
  }

  private static @NotNull Supplier<PsiElement> prevWalker(@NotNull PsiElement element, @NotNull PsiElement scope) {
    return new Supplier<>() {
      PsiElement e = element;

      @Override
      public @Nullable PsiElement get() {
        if (e == null || e == scope) return null;
        PsiElement prev = e.getPrevSibling();
        if (prev != null) {
          return e = PsiTreeUtil.getDeepestLast(prev);
        }
        else {
          PsiElement parent = e.getParent();
          return e = parent == scope || parent instanceof PsiFile ? null : parent;
        }
      }
    };
  }

  public static class CommentInjectionData {
    private final String myDisplayName;
    private final Map<String, String> myMap;

    CommentInjectionData(@NotNull Map<String, String> map, @NotNull String displayName) {
      myMap = Collections.unmodifiableMap(map);
      myDisplayName = displayName;
    }

    public @NotNull String getPrefix() {
      return ObjectUtils.notNull(myMap.get("prefix"), "");
    }

    public @NotNull String getSuffix() {
      return ObjectUtils.notNull(myMap.get("suffix"), "");
    }

    public @NotNull String getInjectedLanguageId() {
      return ObjectUtils.notNull(myMap.get("language"), "");
    }

    public @NlsSafe @NotNull String getDisplayName() {
      return myDisplayName;
    }

    public @NotNull Map<String, String> getValues() {
      return myMap;
    }
  }
}
