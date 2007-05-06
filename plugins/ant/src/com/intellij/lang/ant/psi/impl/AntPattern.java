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
  
  private AntPattern(final boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
  }

  public void visitAntStructuredElement(final AntStructuredElement element) {
    final AntTypeDefinition typeDef = element.getTypeDefinition();
    if (typeDef != null) {
      final AntTypeId antTypeId = typeDef.getTypeId();
      // todo: add support to includefile and excludefile
      if ("include".equals(antTypeId.getName())) {
        if (isEnabled(element)) {
          final String value = element.getSourceElement().getAttributeValue("name");
          if (value != null) {
            myIncludePatterns.add(convertToRegexPattern(value, myCaseSensitive));
          }
        }
      }
      else if ("exclude".equals(antTypeId.getName())) {
        if (isEnabled(element)) {
          final String value = element.getSourceElement().getAttributeValue("name");
          if (value != null) {
            myExcludePatterns.add(convertToRegexPattern(value, myCaseSensitive));
          }
        }
      }
      else {
        // todo: add support to includesfile and excludesfile
        final String includeAttribs = element.getSourceElement().getAttributeValue("includes");
        if (includeAttribs != null) {
          addPatterns(myIncludePatterns, includeAttribs);
        }
        final String excludeAttribs = element.getSourceElement().getAttributeValue("excludes");
        if (excludeAttribs != null) {
          addPatterns(myExcludePatterns, excludeAttribs);
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
  
  private void addPatterns(final List<Pattern> patternContainer, final String patternString) {
    final StringTokenizer tokenizer = new StringTokenizer(patternString, ", \t", false);
    while (tokenizer.hasMoreTokens()) {
      final String pattern = tokenizer.nextToken();
      if (pattern.length() > 0) {
        patternContainer.add(convertToRegexPattern(pattern, myCaseSensitive));
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
  
}
