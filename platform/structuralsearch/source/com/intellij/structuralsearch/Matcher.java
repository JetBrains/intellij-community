package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatcherImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * This class makes program structure tree matching:
 */
public class Matcher extends MatcherImpl {

  public Matcher(Project project) {
    super(project);
  }

  public Matcher(final Project project, final MatchOptions matchOptions) {
    super(project, matchOptions);
  }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @throws MalformedPatternException
   * @throws UnsupportedPatternException
   */
  public void findMatches(MatchResultSink sink,MatchOptions options) throws
    MalformedPatternException, UnsupportedPatternException
  {
    super.findMatches(sink,options);
  }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @param source string for search
   * @param pattern to be searched
   * @return list of matches found
   * @throws MalformedPatternException
   * @throws UnsupportedPatternException
   */
  public List<MatchResult> testFindMatches(String source,
                              String pattern,
                              MatchOptions options,
                              boolean filePattern,
                              FileType sourceFileType,
                              String sourceExtension,
                              boolean physicalSourceFile)
    throws MalformedPatternException, UnsupportedPatternException {
    return super.testFindMatches(source, pattern, options, filePattern, sourceFileType, sourceExtension, physicalSourceFile);
  }

  public List<MatchResult> testFindMatches(String source, String pattern, MatchOptions options, boolean filePattern)
    throws MalformedPatternException, UnsupportedPatternException {
    return super.testFindMatches(source, pattern, options, filePattern);
  }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @param sink
   * @param options
   * @throws MalformedPatternException
   * @throws UnsupportedPatternException
   */
  public void testFindMatches(MatchResultSink sink,MatchOptions options)
    throws MalformedPatternException, UnsupportedPatternException {

    super.testFindMatches(sink,options);
  }

  /**
   * Tests if given element is matched by given pattern starting from target variable. If matching succeeds
   * then not null match result is returned.
   * @throws MalformedPatternException
   * @throws UnsupportedPatternException
   */
  @NotNull
  public List<MatchResult> matchByDownUp(PsiElement element, MatchOptions options) throws
    MalformedPatternException, UnsupportedPatternException
  {
    return super.matchByDownUp(element, options);
  }
}
