/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Producer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringSearcher;
import gnu.trove.TIntArrayList;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Gregory.Shrago
 */
public class InjectorUtils {
  private static final Logger LOG = Logger.getInstance(InjectorUtils.class);
  public static final Comparator<TextRange> RANGE_COMPARATOR = (o1, o2) -> {
    if (o1.intersects(o2)) return 0;
    return o1.getStartOffset() - o2.getStartOffset();
  };

  private InjectorUtils() {
  }

  @Nullable
  public static Language getLanguage(@NotNull BaseInjection injection) {
    return getLanguageByString(injection.getInjectedLanguageId());
  }

  @Nullable
  public static Language getLanguageByString(String languageId) {
    Language language = InjectedLanguage.findLanguageById(languageId);
    if (language != null) return language;
    ReferenceInjector injector = ReferenceInjector.findById(languageId);
    if (injector != null) return injector.toLanguage();
    FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(languageId);
    return fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : null;
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
    List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list = ContainerUtil.newArrayListWithCapacity(ranges.size());

    for (TextRange range : ranges) {
      list.add(Trinity.create(host, injectedLanguage, range));
    }
    //if (host.getChildren().length > 0) {
    //  host.putUserData(LanguageInjectionSupport.HAS_UNPARSABLE_FRAGMENTS, Boolean.TRUE);
    //}
    registerInjection(language, list, host.getContainingFile(), registrar);
    if (support != null) {
      registerSupport(support, true, host, language);
    }
    return !ranges.isEmpty();
  }

  public static void registerInjection(Language language,
                                       List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list,
                                       PsiFile containingFile,
                                       MultiHostRegistrar registrar) {
    // if language isn't injected when length == 0, subsequent edits will not cause the language to be injected as well.
    // Maybe IDEA core is caching a bit too aggressively here?
    if (language == null/* && (pair.second.getLength() > 0*/) {
      return;
    }
    ParserDefinition parser = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    ReferenceInjector injector = ReferenceInjector.findById(language.getID());
    if (parser == null && injector != null && list.size() == 1) {
      String prefix = list.get(0).second.getPrefix();
      String suffix = list.get(0).second.getSuffix();
      PsiLanguageInjectionHost host = list.get(0).first;
      TextRange textRange = list.get(0).third;
      InjectedLanguageUtil.injectReference(registrar, language, prefix, suffix, host, textRange);
      return;
    }
    boolean injectionStarted = false;
    for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> t : list) {
      PsiLanguageInjectionHost host = t.first;
      if (host.getContainingFile() != containingFile) continue;

      TextRange textRange = t.third;
      InjectedLanguage injectedLanguage = t.second;

      if (!injectionStarted) {
        // TextMate language requires file extension
        if (!StringUtil.equalsIgnoreCase(language.getID(), t.second.getID())) {
          registrar.startInjecting(language, StringUtil.toLowerCase(t.second.getID()));
        }
        else {
          registrar.startInjecting(language);
        }
        injectionStarted = true;
      }
      registrar.addPlace(injectedLanguage.getPrefix(), injectedLanguage.getSuffix(), host, textRange);
    }
    if (injectionStarted) {
      registrar.doneInjecting();
    }
  }

  private static final Map<String, LanguageInjectionSupport> ourSupports;
  static {
    ourSupports = new LinkedHashMap<>();
    for (LanguageInjectionSupport support : Arrays.asList(Extensions.getExtensions(LanguageInjectionSupport.EP_NAME))) {
      ourSupports.put(support.getId(), support);
    }
  }

  @NotNull
  public static Collection<String> getActiveInjectionSupportIds() {
    return ourSupports.keySet();
  }
  public static Collection<LanguageInjectionSupport> getActiveInjectionSupports() {
    return ourSupports.values();
  }

  @Nullable
  public static LanguageInjectionSupport findInjectionSupport(final String id) {
    return ourSupports.get(id);
  }

  @NotNull
  public static Class[] getPatternClasses(final String supportId) {
    final LanguageInjectionSupport support = findInjectionSupport(supportId);
    return support == null ? ArrayUtil.EMPTY_CLASS_ARRAY : support.getPatternClasses();
  }

  @NotNull
  public static LanguageInjectionSupport findNotNullInjectionSupport(final String id) {
    final LanguageInjectionSupport result = findInjectionSupport(id);
    assert result != null: id+" injector not found";
    return result;
  }

  public static StringBuilder appendStringPattern(@NotNull StringBuilder sb, @NotNull String prefix, @NotNull String text, @NotNull String suffix) {
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

  public static boolean isRegexp(final String s) {
    boolean hasReChars = false;
    for (int i = 0, len = s.length(); i < len; i++) {
      final char c = s.charAt(i);
      if (c == ' ' || c == '_' || c == '-' || Character.isLetterOrDigit(c)) continue;
      hasReChars = true;
      break;
    }
    if (hasReChars) {
      try {
        new URL(s);
      }
      catch (MalformedURLException e) {
        return true;
      }
    }
    return false;
  }

  public static void registerSupport(@NotNull LanguageInjectionSupport support, boolean settingsAvailable, @NotNull PsiElement element, @NotNull Language language) {
    putInjectedFileUserData(element, language, LanguageInjectionSupport.INJECTOR_SUPPORT, support);
    if (settingsAvailable) {
      putInjectedFileUserData(element, language, LanguageInjectionSupport.SETTINGS_EDITOR, support);
    }
  }

  /**
   * @deprecated use {@link InjectorUtils#registerSupport(org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport, boolean, com.intellij.psi.PsiElement, com.intellij.lang.Language)} instead
   */
  public static void registerSupport(@NotNull LanguageInjectionSupport support, boolean settingsAvailable, @NotNull MultiHostRegistrar registrar) {
    LOG.warn("use {@link InjectorUtils#registerSupport(org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport, boolean, com.intellij.psi.PsiElement, com.intellij.lang.Language)} instead");
    putInjectedFileUserData(registrar, LanguageInjectionSupport.INJECTOR_SUPPORT, support);
    if (settingsAvailable) {
      putInjectedFileUserData(registrar, LanguageInjectionSupport.SETTINGS_EDITOR, support);
    }
  }

  public static <T> void putInjectedFileUserData(@NotNull PsiElement element, @NotNull Language language, Key<T> key, T value) {
    InjectedLanguageUtil.putInjectedFileUserData(element, language, key, value);
  }

  /**
   * @deprecated use {@link InjectorUtils#putInjectedFileUserData(com.intellij.psi.PsiElement, com.intellij.lang.Language, com.intellij.openapi.util.Key, java.lang.Object)} instead
   */
  public static <T> void putInjectedFileUserData(MultiHostRegistrar registrar, Key<T> key, T value) {
    InjectedLanguageUtil.putInjectedFileUserData(registrar, key, value);
  }

  @SuppressWarnings("UnusedParameters")
  public static Configuration getEditableInstance(Project project) {
    return Configuration.getInstance();
  }

  public static boolean canBeRemoved(BaseInjection injection) {
    if (injection.isEnabled()) return false;
    if (StringUtil.isNotEmpty(injection.getPrefix()) || StringUtil.isNotEmpty(injection.getSuffix())) return false;
    if (StringUtil.isNotEmpty(injection.getValuePattern())) return false;
    return true;
  }

  @Nullable
  public static CommentInjectionData findCommentInjectionData(@NotNull PsiElement context, @Nullable Ref<PsiElement> causeRef) {
    return findCommentInjectionData(context, true, causeRef);
  }

  @Nullable
  public static CommentInjectionData findCommentInjectionData(@NotNull PsiElement context, boolean treeElementsIncludeComment, @Nullable Ref<PsiElement> causeRef) {
    PsiElement target = CompletionUtil.getOriginalOrSelf(context);
    PsiFile file = target.getContainingFile();
    if (file == null) return null;
    TreeMap<TextRange, CommentInjectionData> map = getInjectionMap(file);
    if (map == null) return null;
    Map.Entry<TextRange, CommentInjectionData> entry = map.lowerEntry(target.getTextRange());
    if (entry == null) return null;

    PsiComment psiComment = PsiTreeUtil.findElementOfClassAtOffset(file, entry.getKey().getStartOffset(), PsiComment.class, false);
    if (psiComment == null) return null;
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
    else if (off2 - off1 > 2) {
      // ... there's no non-empty valid host in between comment and topmostElement
      Producer<PsiElement> producer = prevWalker(topmostElement, commonParent);
      PsiElement e;
      while ( (e = producer.produce()) != null && e != psiComment) {
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
    return entry.getValue();
  }

  @Nullable
  public static BaseInjection findCommentInjection(@NotNull PsiElement context,
                                                   @NotNull String supportId,
                                                   @Nullable Ref<PsiElement> causeRef) {
    CommentInjectionData data = findCommentInjectionData(context, causeRef);
    if (data == null) return null;
    BaseInjection injection = new BaseInjection(supportId);
    injection.setPrefix(data.getPrefix());
    injection.setSuffix(data.getSuffix());
    injection.setInjectedLanguageId(data.getInjectedLanguageId());
    injection.setDisplayName(data.getDisplayName());
    return injection;
  }

  @Nullable
  private static TreeMap<TextRange, CommentInjectionData> getInjectionMap(@NotNull final PsiFile file) {
    return CachedValuesManager.getCachedValue(file, () -> {
      TreeMap<TextRange, CommentInjectionData> map = calcInjections(file);
      return CachedValueProvider.Result.create(map.isEmpty() ? null : map, file);
    });
  }

  @NotNull
  protected static TreeMap<TextRange,CommentInjectionData> calcInjections(PsiFile file) {
    final TreeMap<TextRange, CommentInjectionData> injectionMap = new TreeMap<>(RANGE_COMPARATOR);

    TIntArrayList ints = new TIntArrayList();
    StringSearcher searcher = new StringSearcher("language=", true, true, false);
    CharSequence contents = file.getViewProvider().getContents();
    final char[] contentsArray = CharArrayUtil.fromSequenceWithoutCopying(contents);

    int s0 = 0, s1 = contents.length();
    for (int idx = searcher.scan(contents, contentsArray, s0, s1);
         idx != -1;
         idx = searcher.scan(contents, contentsArray, idx + 1, s1)) {
      ints.add(idx);
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
  private static Map<String, String> decodeMap(CharSequence charSequence) {
    if (StringUtil.isEmpty(charSequence)) return Collections.emptyMap();
    final Matcher matcher = MAP_ENTRY_PATTERN.matcher(charSequence);
    final LinkedHashMap<String, String> map = new LinkedHashMap<>();
    while (matcher.find()) {
      map.put(StringUtil.unescapeStringCharacters(matcher.group(1)),
              StringUtil.unescapeStringCharacters(StringUtil.unquoteString(matcher.group(2))));
    }
    return map;
  }

  private static Producer<PsiElement> prevWalker(final PsiElement element, final PsiElement scope) {
    return new Producer<PsiElement>() {
      PsiElement e = element;

      @Nullable
      @Override
      public PsiElement produce() {
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
    private Map<String, String> myMap;

    public CommentInjectionData(@NotNull Map<String, String> map, String displayName) {
      myMap = Collections.unmodifiableMap(map);
      myDisplayName = displayName;
    }
    
    @NotNull
    public String getPrefix() {
      return ObjectUtils.notNull(myMap.get("prefix"), "");
    }

    @NotNull
    public String getSuffix() {
      return ObjectUtils.notNull(myMap.get("suffix"), "");
    }

    @NotNull
    public String getInjectedLanguageId() {
      return ObjectUtils.notNull(myMap.get("language"), "");
    }

    public String getDisplayName() {
      return myDisplayName;
    }
    
    public Map<String, String> getValues() {
      return myMap;
    }
  }
}
