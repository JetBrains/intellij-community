// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;

import java.util.ArrayList;
import java.util.List;

public class StatEdge {
  public static final int TYPE_REGULAR = 1;
  public static final int TYPE_EXCEPTION = 2;
  public static final int TYPE_BREAK = 4;
  public static final int TYPE_CONTINUE = 8;
  public static final int TYPE_FINALLYEXIT = 32;

  public static final int[] TYPES = new int[]{
    TYPE_REGULAR,
    TYPE_EXCEPTION,
    TYPE_BREAK,
    TYPE_CONTINUE,
    TYPE_FINALLYEXIT
  };

  private int type;

  private Statement source;

  private Statement destination;

  private List<String> exceptions;

  public Statement closure;

  public boolean labeled = true;

  public boolean explicit = true;

  public StatEdge(int type, Statement source, Statement destination, Statement closure) {
    this(type, source, destination);
    this.closure = closure;
  }

  public StatEdge(int type, Statement source, Statement destination) {
    this.type = type;
    this.source = source;
    this.destination = destination;
  }

  public StatEdge(Statement source, Statement destination, List<String> exceptions) {
    this(TYPE_EXCEPTION, source, destination);
    if (exceptions != null) {
      this.exceptions = new ArrayList<>(exceptions);
    }
  }

  public int getType() {
    return type;
  }

  public void setType(int type) {
    this.type = type;
  }

  public Statement getSource() {
    return source;
  }

  public void setSource(Statement source) {
    this.source = source;
  }

  public Statement getDestination() {
    return destination;
  }

  public void setDestination(Statement destination) {
    this.destination = destination;
  }

  public List<String> getExceptions() {
    return this.exceptions;
  }

  //	public void setException(String exception) {
  //		this.exception = exception;
  //	}
}
