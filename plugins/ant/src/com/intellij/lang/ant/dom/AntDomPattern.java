/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 */
public class AntDomPattern extends AntDomRecursiveVisitor {
  private static final List<Pattern> ourDefaultExcludes = new ArrayList<>(getDefaultExcludes(true));
  private static final List<Pattern> ourCaseInsensitiveDefaultExcludes = new ArrayList<>(getDefaultExcludes(false));
  private final boolean myCaseSensitive;
  private static final String ourSeparatorPattern = Pattern.quote("/");

  private static List<Pattern> getDefaultExcludes(final boolean caseSensitive) {
    return Arrays.asList(
      convertToRegexPattern("**/*~", caseSensitive),
      convertToRegexPattern("**/#*#", caseSensitive),
      convertToRegexPattern("**/.#*", caseSensitive),
      convertToRegexPattern("**/%*%", caseSensitive),
      convertToRegexPattern("**/._*", caseSensitive),
      convertToRegexPattern("**/CVS", caseSensitive),
      convertToRegexPattern("**/CVS/**", caseSensitive),
      convertToRegexPattern("**/.cvsignore", caseSensitive),
      convertToRegexPattern("**/SCCS", caseSensitive),
      convertToRegexPattern("**/SCCS/**", caseSensitive),
      convertToRegexPattern("**/vssver.scc", caseSensitive),
      convertToRegexPattern("**/.svn", caseSensitive),
      convertToRegexPattern("**/.svn/**", caseSensitive),
      convertToRegexPattern("**/_svn", caseSensitive),
      convertToRegexPattern("**/_svn/**", caseSensitive),
      convertToRegexPattern("**/.DS_Store", caseSensitive)
    );
  }

  private final List<Pattern> myIncludePatterns = new ArrayList<>();
  private final List<Pattern> myExcludePatterns = new ArrayList<>();
  private final List<PrefixItem[]> myCouldBeIncludedPatterns = new ArrayList<>();

  AntDomPattern(final boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
  }

  public boolean hasIncludePatterns() {
    return myIncludePatterns.size() > 0;
  }

  public void visitAntDomElement(AntDomElement element) {
    // todo: add support to includefile and excludefile
    if ("include".equals(element.getXmlElementName()) && !(element instanceof AntDomInclude)) {
      if (isEnabled(element)) {
        final String value = getAttributeValue(element, "name");
        if (value != null) {
          addIncludePattern(value);
        }
      }
    }
    else if ("exclude".equals(element.getXmlElementName())) {
      if (isEnabled(element)) {
        final String value = getAttributeValue(element, "name");
        if (value != null) {
          addExcludePattern(value);
        }
      }
    }
    else {
      // todo: add support to includesfile and excludesfile
      final String includeAttribs = getAttributeValue(element, "includes");
      if (includeAttribs != null) {
        addPatterns(true, includeAttribs);
      }
      final String excludeAttribs = getAttributeValue(element, "excludes");
      if (excludeAttribs != null) {
        addPatterns(false, excludeAttribs);
      }
    }
    final AntDomElement referred = element.getRefId().getValue();
    if (referred != null) {
      referred.accept(this);
    }
    super.visitAntDomElement(element);
  }

  @Nullable
  private static String getAttributeValue(AntDomElement element, final String attributeName) {
    final DomAttributeChildDescription description = element.getGenericInfo().getAttributeChildDescription(attributeName);
    if (description == null) {
      return null;
    }
    return description.getDomAttributeValue(element).getStringValue();
  }

  public final void addExcludePattern(final String antPattern) {
    myExcludePatterns.add(convertToRegexPattern(antPattern, myCaseSensitive));
  }

  public final void addIncludePattern(final String antPattern) {
    myIncludePatterns.add(convertToRegexPattern(antPattern, myCaseSensitive));
    String normalizedPattern = antPattern.endsWith("/") || antPattern.endsWith(File.separator)? antPattern.replace(File.separatorChar, '/') + "**" : antPattern.replace(File.separatorChar, '/');
    if (normalizedPattern.startsWith("/") && normalizedPattern.length() > 1) {
      // cut first leading slash if any
      normalizedPattern = normalizedPattern.substring(1, normalizedPattern.length());
    }
    if (!normalizedPattern.startsWith("/")) {
      final String[] patDirs = normalizedPattern.split(ourSeparatorPattern);
      final PrefixItem[] items = new PrefixItem[patDirs.length];
      for (int i = 0; i < patDirs.length; i++) {
        items[i] = new PrefixItem(patDirs[i]);
      }
      myCouldBeIncludedPatterns.add(items);
    }
  }

  public boolean acceptPath(final String relativePath) {
    final String path = relativePath.replace('\\', '/');
    boolean accepted = myIncludePatterns.size() == 0;
    for (Pattern includePattern : myIncludePatterns) {
      if (includePattern.matcher(path).matches()) {
        accepted = true;
        break;
      }
    }
    if (accepted) {
      for (Pattern excludePattern : myExcludePatterns) {
        if (excludePattern.matcher(path).matches()) {
          accepted = false;
          break;
        }
      }
    }
    return accepted;
  }

  private static boolean isEnabled(AntDomElement element) {
    final String ifProperty = getAttributeValue(element, "if");
    if (ifProperty != null && PropertyResolver.resolve(element.getContextAntProject(), ifProperty, element).getFirst() == null) {
      return false;
    }
    final String unlessProperty = getAttributeValue(element, "unless");
    if (unlessProperty != null && PropertyResolver.resolve(element.getContextAntProject(), unlessProperty, element).getFirst() != null) {
      return false;
    }
    return true;
  }

  private void addPatterns(final boolean addToIncludes, final String patternString) {
    final StringTokenizer tokenizer = new StringTokenizer(patternString, ", \t", false);
    while (tokenizer.hasMoreTokens()) {
      final String pattern = tokenizer.nextToken();
      if (pattern.length() > 0) {
        if (addToIncludes) {
          addIncludePattern(pattern);
        }
        else {
          addExcludePattern(pattern);
        }
      }
    }
  }

  private static Pattern convertToRegexPattern(@NonNls final String antPattern, final boolean caseSensitive) {
    return Pattern.compile(FileUtil.convertAntToRegexp(antPattern), caseSensitive? 0 : Pattern.CASE_INSENSITIVE);
  }

  public static AntDomPattern create(AntDomElement element, final boolean honorDefaultExcludes, final boolean caseSensitive) {
    final AntDomPattern antPattern = new AntDomPattern(caseSensitive);
    element.accept(antPattern);
    if (honorDefaultExcludes) {
      antPattern.myExcludePatterns.addAll(caseSensitive? ourDefaultExcludes : ourCaseInsensitiveDefaultExcludes);
    }
    return antPattern;
  }

  // from org.apache.tools.ant.DirectoryScanner
  protected static boolean matchPatternStart(PrefixItem[] patDirs, String str) {
    final String[] strDirs = str.split(ourSeparatorPattern);

    int patIdxStart = 0;
    final int patIdxEnd   = patDirs.length-1;
    int strIdxStart = 0;
    final int strIdxEnd   = strDirs.length-1;

    // up to first '**'
    while (patIdxStart <= patIdxEnd && strIdxStart <= strIdxEnd) {
      final AntDomPattern.PrefixItem item = patDirs[patIdxStart];
      if ("**".equals(item.getStrPattern())) {
        break;
      }
      if (!item.getPattern().matcher(strDirs[strIdxStart]).matches()) {
        return false;
      }
      patIdxStart++;
      strIdxStart++;
    }

    if (strIdxStart > strIdxEnd) {
      // String is exhausted
      return true;
    } 

    if (patIdxStart > patIdxEnd) {
      // String not exhausted, but pattern is. Failure.
      return false;
    } 

    // pattern now holds ** while string is not exhausted
    // this will generate false positives but we can live with that.
    return true;
  }

  public boolean couldBeIncluded(String relativePath) {
    if (myIncludePatterns.size() == 0) {
      return true;
    }
    return myCouldBeIncludedPatterns.stream().anyMatch(couldBeIncludedPattern -> matchPatternStart(couldBeIncludedPattern, relativePath));
  }
  
  private class PrefixItem {
    private final String myStrPattern;
    private Pattern myCompiledPattern;
    public PrefixItem(String strPattern) {
      myStrPattern = strPattern;
    }

    public String getStrPattern() {
      return myStrPattern;
    }

    public Pattern getPattern() {
      if (myCompiledPattern == null) {
        myCompiledPattern = Pattern.compile(FileUtil.convertAntToRegexp(myStrPattern), myCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
      }
      return myCompiledPattern;
    }
  }
}
