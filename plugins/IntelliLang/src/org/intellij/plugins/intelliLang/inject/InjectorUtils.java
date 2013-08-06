/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.MultiHostRegistrarImpl;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
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
  public static final Comparator<TextRange> RANGE_COMPARATOR = new Comparator<TextRange>() {
    public int compare(final TextRange o1, final TextRange o2) {
      if (o1.intersects(o2)) return 0;
      return o1.getStartOffset() - o2.getStartOffset();
    }
  };

  private InjectorUtils() {
  }


  public static boolean registerInjectionSimple(@NotNull PsiLanguageInjectionHost host,
                                                @NotNull BaseInjection injection,
                                                @Nullable LanguageInjectionSupport support,
                                                @NotNull MultiHostRegistrar registrar) {
    Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
    if (language == null) return false;

    InjectedLanguage injectedLanguage =
      InjectedLanguage.create(injection.getInjectedLanguageId(), injection.getPrefix(), injection.getSuffix(), false);

    List<TextRange> ranges = injection.getInjectedArea(host);
    List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list = ContainerUtil.newArrayListWithExpectedSize(ranges.size());

    for (TextRange range : ranges) {
      list.add(Trinity.create(host, injectedLanguage, range));
    }
    //if (host.getChildren().length > 0) {
    //  host.putUserData(LanguageInjectionSupport.HAS_UNPARSABLE_FRAGMENTS, Boolean.TRUE);
    //}
    registerInjection(language, list, host.getContainingFile(), registrar);
    if (support != null) {
      registerSupport(support, true, registrar);
    }
    return !ranges.isEmpty();
  }

  public static void registerInjection(Language language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list, PsiFile containingFile, MultiHostRegistrar registrar) {
    // if language isn't injected when length == 0, subsequent edits will not cause the language to be injected as well.
    // Maybe IDEA core is caching a bit too aggressively here?
    if (language == null/* && (pair.second.getLength() > 0*/) {
      return;
    }
    boolean injectionStarted = false;
    for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : list) {
      final PsiLanguageInjectionHost host = trinity.first;
      if (host.getContainingFile() != containingFile) continue;

      final TextRange textRange = trinity.third;
      final InjectedLanguage injectedLanguage = trinity.second;

      if (!injectionStarted) {
        registrar.startInjecting(language);
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
    ourSupports = new LinkedHashMap<String, LanguageInjectionSupport>();
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

  public static void registerSupport(@NotNull LanguageInjectionSupport support, boolean settingsAvailable, @NotNull MultiHostRegistrar registrar) {
    putInjectedFileUserData(registrar, LanguageInjectionSupport.INJECTOR_SUPPORT, support);
    if (settingsAvailable) {
      putInjectedFileUserData(registrar, LanguageInjectionSupport.SETTINGS_EDITOR, support);
    }
  }

  public static <T> void putInjectedFileUserData(MultiHostRegistrar registrar, Key<T> key, T value) {
    PsiFile psiFile = getInjectedFile(registrar);
    if (psiFile != null) psiFile.putUserData(key, value);
  }

  public static PsiFile getInjectedFile(MultiHostRegistrar registrar) {
    final List<Pair<Place,PsiFile>> result = ((MultiHostRegistrarImpl)registrar).getResult();
    return result == null || result.isEmpty() ? null : result.get(result.size() - 1).second;
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

  public static BaseInjection findCommentInjection(PsiElement context, final String supportId, final Ref<PsiElement> causeRef) {
    return findNearestComment(context, new NullableFunction<PsiComment, BaseInjection>() {
      @Nullable
      @Override
      public BaseInjection fun(PsiComment comment) {
        if (causeRef != null) causeRef.set(comment);
        String text = ElementManipulators.getValueText(comment).trim();
        return detectInjectionFromText(supportId, text);
      }
    });
  }

  private static final Pattern MAP_ENTRY_PATTERN = Pattern.compile("([\\S&&[^=]]+)=(\"(?:[^\"]|\\\\\")*\"|\\S*)");
  public static Map<String, String> decodeMap(CharSequence charSequence) {
    if (StringUtil.isEmpty(charSequence)) return Collections.emptyMap();
    final Matcher matcher = MAP_ENTRY_PATTERN.matcher(charSequence);
    final LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
    while (matcher.find()) {
      map.put(StringUtil.unescapeStringCharacters(matcher.group(1)),
              StringUtil.unescapeStringCharacters(StringUtil.unquoteString(matcher.group(2))));
    }
    return map;
  }

  @Nullable
  public static BaseInjection detectInjectionFromText(String supportId, String text) {
    if (text == null || !text.startsWith("language=")) return null;
    Map<String, String> map = decodeMap(text);
    String languageId = map.get("language");
    String prefix = ObjectUtils.notNull(map.get("prefix"), "");
    String suffix = ObjectUtils.notNull(map.get("suffix"), "");
    BaseInjection injection = new BaseInjection(supportId);
    injection.setDisplayName(text);
    injection.setInjectedLanguageId(languageId);
    injection.setPrefix(prefix);
    injection.setSuffix(suffix);
    return injection;
  }

  @Nullable
  public static <T> T findNearestComment(PsiElement element, NullableFunction<PsiComment, T> processor) {
    if (element instanceof PsiComment) return null;
    PsiFile containingFile = element.getContainingFile();

    List<PsiLanguageInjectionHost> otherHosts = new SmartList<PsiLanguageInjectionHost>();

    boolean commentOrSpaces = false;

    PsiElement prev = element, e = prevOrParent(element, containingFile);
    for (int counter = 0; e != null && counter < 100; prev = e, e = prevOrParent(e, containingFile), counter ++) {
      if (e instanceof PsiComment) {
        commentOrSpaces = true;
        PsiComment comment = (PsiComment)e;
        if (!checkDepth(otherHosts, element, comment)) continue;
        T value = processor.fun(comment);
        if (value != null) return value;
      }
      else if (e instanceof PsiWhiteSpace) {
        commentOrSpaces = true;
      }
      else if (e instanceof PsiLanguageInjectionHost) {
        commentOrSpaces = StringUtil.isEmptyOrSpaces(e.getText()); // check getText only for hosts (XmlText)
        if (!commentOrSpaces) otherHosts.add((PsiLanguageInjectionHost)e);
      }
      else {
        commentOrSpaces = false;
      }
    }
    if (commentOrSpaces) { // allow several comments
      for (e = prevOrParent(prev, containingFile); e != null; e = e.getPrevSibling()) {
        if (e instanceof PsiComment) {
          PsiComment comment = (PsiComment)e;
          if (!checkDepth(otherHosts, element, comment)) continue;
          T value = processor.fun(comment);
          if (value != null) return value;
        }
        else if (!(e instanceof PsiWhiteSpace)) {
          break;
        }
      }
    }
    return null;
  }

  // allow java-like multi variable commenting: String s = "s", t = "t"
  // a comment should cover all hosts in a subtree
  private static boolean checkDepth(List<PsiLanguageInjectionHost> hosts, PsiElement element, PsiComment comment) {
    if (hosts.isEmpty()) return true;
    PsiElement parent = PsiTreeUtil.findCommonParent(comment, element);
    for (PsiLanguageInjectionHost host : hosts) {
      if (!PsiTreeUtil.isAncestor(parent, PsiTreeUtil.findCommonParent(host, element), true)) return false;
    }
    return true;
  }

  @Nullable
  public static PsiElement prevOrParent(PsiElement e, PsiElement scope) {
    if (e == null || e == scope) return null;
    PsiElement prev = e.getPrevSibling();
    if (prev != null) return PsiTreeUtil.getDeepestLast(prev);
    PsiElement parent = e.getParent();
    return parent == scope || parent instanceof PsiFile ? null : parent;
  }
}
