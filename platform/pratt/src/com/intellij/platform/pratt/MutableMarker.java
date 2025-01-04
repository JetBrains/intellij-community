// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pratt;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;

public class MutableMarker {
  enum Mode { READY, DROPPED, COMMITTED, ERROR }

  private final PsiBuilder.Marker myStartMarker;
  private IElementType myResultType;
  private final int myInitialPathLength;
  private final LinkedList<IElementType> myPath;
  private Mode myMode = Mode.READY;

  public MutableMarker(final LinkedList<IElementType> path, final PsiBuilder.Marker startMarker, final int initialPathLength) {
    myPath = path;
    myStartMarker = startMarker;
    myInitialPathLength = initialPathLength;
  }

  public boolean isCommitted() {
    return myMode == Mode.COMMITTED;
  }

  public boolean isDropped() {
    return myMode == Mode.DROPPED;
  }

  public boolean isError() {
    return myMode == Mode.ERROR;
  }

  public boolean isReady() {
    return myMode == Mode.READY;
  }

  public MutableMarker setResultType(final IElementType resultType) {
    myResultType = resultType;
    return this;
  }

  public IElementType getResultType() {
    return myResultType;
  }

  public void finish() {
    if (myMode == Mode.READY) {
      if (myResultType == null) {
        myMode = Mode.DROPPED;
        myStartMarker.drop();
      }
      else {
        myMode = Mode.COMMITTED;
        myStartMarker.done(myResultType);
        restorePath();
        myPath.addLast(myResultType);
      }
    }
  }

  private void restorePath() {
    while (myPath.size() > myInitialPathLength) {
      myPath.removeLast();
    }
  }

  public MutableMarker precede() {
    return new MutableMarker(myPath, myStartMarker.precede(), myInitialPathLength);
  }

  public void finish(final IElementType type) {
    setResultType(type);
    finish();
  }

  public void drop() {
    assert myMode == Mode.READY : myMode;
    myMode = Mode.DROPPED;
    myStartMarker.drop();
  }

  public void rollback() {
    assert myMode == Mode.READY : myMode;
    myMode = Mode.DROPPED;
    restorePath();
    myStartMarker.rollbackTo();
  }

  public void error(@NotNull @NlsContexts.ParsingError String message) {
    assert myMode == Mode.READY : myMode;
    myMode = Mode.ERROR;
    myStartMarker.error(message);
  }
}
