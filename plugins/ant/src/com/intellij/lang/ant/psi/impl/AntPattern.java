/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElementVisitor;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.reference.AntRefIdReference;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 *         Date: May 2, 2007
 */
public class AntPattern extends AntElementVisitor {
  private static final List<Pattern> ourDefaultExcludes = new ArrayList<Pattern>(getDefaultExcludes(true));
  private static final List<Pattern> ourCaseInsensitiveDefaultExcludes = new ArrayList<Pattern>(getDefaultExcludes(false));
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
      convertToRegexPattern("**/.DS_Store", caseSensitive)
    );
  }

  private List<Pattern> myIncludePatterns = new ArrayList<Pattern>(); 
  private List<Pattern> myExcludePatterns = new ArrayList<Pattern>();
  private List<PrefixItem[]> myCouldBeIncludedPatterns = new ArrayList<PrefixItem[]>();
  
  AntPattern(final boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
  }
  
  public boolean hasIncludePatterns() {
    return myIncludePatterns.size() > 0;
  }
  
  public void visitAntStructuredElement(final AntStructuredElement element) {
    final AntTypeDefinition typeDef = element.getTypeDefinition();
    if (typeDef != null) {
      final AntTypeId antTypeId = typeDef.getTypeId();
      // todo: add support to includefile and excludefile
      if ("include".equals(antTypeId.getName())) {
        if (isEnabled(element)) {
          final String value = element.computeAttributeValue(element.getSourceElement().getAttributeValue("name"));
          if (value != null) {
            addIncludePattern(value);
          }
        }
      }
      else if ("exclude".equals(antTypeId.getName())) {
        if (isEnabled(element)) {
          final String value = element.computeAttributeValue(element.getSourceElement().getAttributeValue("name"));
          if (value != null) {
            addExcludePattern(value);
          }
        }
      }
      else {
        // todo: add support to includesfile and excludesfile
        final String includeAttribs = element.computeAttributeValue(element.getSourceElement().getAttributeValue("includes"));
        if (includeAttribs != null) {
          addPatterns(true, includeAttribs);
        }
        final String excludeAttribs = element.computeAttributeValue(element.getSourceElement().getAttributeValue("excludes"));
        if (excludeAttribs != null) {
          addPatterns(false, excludeAttribs);
        }
      }
      final PsiReference[] refs = element.getReferences();
      for (PsiReference ref : refs) {
        if (ref instanceof AntRefIdReference) {
          final PsiElement psiElement = ref.resolve();
          if (psiElement instanceof AntStructuredElement) {
            ((AntStructuredElement)psiElement).acceptAntElementVisitor(this);
          }
        }
      }
    }

    super.visitAntStructuredElement(element);
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
  
  private static boolean isEnabled(AntStructuredElement element) {
    final XmlTag srcElem = element.getSourceElement();
    final AntFile antFile = element.getAntFile();
    final String ifProperty = srcElem.getAttributeValue("if");
    if (ifProperty != null && antFile.getProperty(ifProperty) == null) {
      return false;
    }
    final String unlessProperty = srcElem.getAttributeValue("unless");
    if (unlessProperty != null && antFile.getProperty(unlessProperty) != null) {
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

  public static AntPattern create(AntStructuredElement element, final boolean honorDefaultExcludes, final boolean caseSensitive) {
    final AntPattern antPattern = new AntPattern(caseSensitive);
    element.acceptAntElementVisitor(antPattern);
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
      final AntPattern.PrefixItem item = patDirs[patIdxStart];
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
    for (PrefixItem[] couldBeIncludedPattern : myCouldBeIncludedPatterns) {
      if (matchPatternStart(couldBeIncludedPattern, relativePath)) {
        return true;
      }
    }
    return false;
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
