/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.MultiHostRegistrarImpl;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

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

  @NotNull
  public static Set<String> getActiveInjectionSupportIds() {
    return ContainerUtil.map2Set(getActiveInjectionSupports(), new NotNullFunction<LanguageInjectionSupport, String>() {
      @NotNull
      public String fun(final LanguageInjectionSupport support) {
        return support.getId();
      }
    });
  }

  public static LanguageInjectionSupport[] getActiveInjectionSupports() {
    return Extensions.getExtensions(LanguageInjectionSupport.EP_NAME);
  }

  @NotNull
  public static Class[] getPatternClasses(final String supportId) {
    final LanguageInjectionSupport support = findInjectionSupport(supportId);
    return support == null ? ArrayUtil.EMPTY_CLASS_ARRAY : support.getPatternClasses();
  }


  @Nullable
  public static LanguageInjectionSupport findInjectionSupport(final String id) {
    return ContainerUtil.find(getActiveInjectionSupports(), new Condition<LanguageInjectionSupport>() {
      public boolean value(final LanguageInjectionSupport support) {
        return support.getId().equals(id);
      }
    });
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
    final List<Pair<Place,PsiFile>> result = ((MultiHostRegistrarImpl)registrar).getResult();
    if (result == null || result.isEmpty()) return;
    final PsiFile psiFile = result.get(result.size() - 1).second;
    psiFile.putUserData(LanguageInjectionSupport.INJECTOR_SUPPORT, support);
    if (settingsAvailable) {
      psiFile.putUserData(LanguageInjectionSupport.SETTINGS_EDITOR, support);
    }
  }
}
