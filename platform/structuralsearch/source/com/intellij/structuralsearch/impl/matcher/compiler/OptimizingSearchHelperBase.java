package com.intellij.structuralsearch.impl.matcher.compiler;

import gnu.trove.THashSet;

/**
 * @author Maxim.Mossienko
*/
abstract class OptimizingSearchHelperBase implements OptimizingSearchHelper {
  private final THashSet<String> scanned;
  private final THashSet<String> scannedText;
  private final THashSet<String> scannedComments;
  private final THashSet<String> scannedLiterals;
  protected int scanRequest;


  protected final CompileContext context;

  OptimizingSearchHelperBase(CompileContext _context) {
    context = _context;

    scanRequest = 0;
    scanned = new THashSet<>();
    scannedText = new THashSet<>();
    scannedComments = new THashSet<>();
    scannedLiterals = new THashSet<>();
  }

  public void clear() {
    scanned.clear();
    scannedComments.clear();
    scannedLiterals.clear();
  }

  public boolean addWordToSearchInCode(final String refname) {
    if (!scanned.contains(refname)) {
      doAddSearchWordInCode(refname);
      scanned.add( refname );
      return true;
    }

    return false;
  }

  public boolean addWordToSearchInText(final String refname) {
    if (!scannedText.contains(refname)) {
      doAddSearchWordInText(refname);
      scannedText.add(refname);
      return true;
    }
    return false;
  }

  protected abstract void doAddSearchWordInCode(final String refname);
  protected abstract void doAddSearchWordInText(final String refname);

  protected abstract void doAddSearchWordInComments(final String refname);
  protected abstract void doAddSearchWordInLiterals(final String refname);

  public void endTransaction() {
    scanRequest++;
  }

  public boolean addWordToSearchInComments(final String refname) {
    if (!scannedComments.contains(refname)) {
      doAddSearchWordInComments(refname);

      scannedComments.add( refname );
      return true;
    }
    return false;
  }

  public boolean addWordToSearchInLiterals(final String refname) {
    if (!scannedLiterals.contains(refname)) {
      doAddSearchWordInLiterals(refname);
      scannedLiterals.add( refname );
      return true;
    }
    return false;
  }

  public boolean isScannedSomething() {
    return scanned.size() > 0 || scannedComments.size() > 0 || scannedLiterals.size() > 0;
  }
}
