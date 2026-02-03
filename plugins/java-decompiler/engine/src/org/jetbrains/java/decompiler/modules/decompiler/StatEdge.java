// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;

import java.util.ArrayList;
import java.util.List;

public class StatEdge {
  private @NotNull EdgeType type;
  private Statement source;
  private Statement destination;
  private List<String> exceptions;
  public Statement closure;
  public boolean labeled = true;
  public boolean explicit = true;
  public boolean canInline = true;

  private StatEdge(@NotNull EdgeType type,
                  Statement source,
                  Statement destination,
                  List<String> exceptions,
                  Statement closure,
                  boolean labeled,
                  boolean explicit) {
    this.type = type;
    this.source = source;
    this.destination = destination;
    this.exceptions = exceptions;
    this.closure = closure;
    this.labeled = labeled;
    this.explicit = explicit;
  }

  public StatEdge(@NotNull EdgeType type, Statement source, Statement destination, Statement closure) {
    this(type, source, destination);
    this.closure = closure;
  }

  public StatEdge(@NotNull EdgeType type, Statement source, Statement destination) {
    this.type = type;
    this.source = source;
    this.destination = destination;
  }

  public StatEdge(Statement source, Statement destination, List<String> exceptions) {
    this(EdgeType.EXCEPTION, source, destination);
    if (exceptions != null) {
      this.exceptions = new ArrayList<>(exceptions);
    }
  }

  public @NotNull EdgeType getType() {
    return type;
  }
  public void setType(@NotNull EdgeType type) {
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

  public StatEdge copy() {
    return new StatEdge(type, source, destination, exceptions, closure, labeled, explicit);
  }

  /**
   * Type of the edges between statements.
   *
   * @see Statement
   */
  public enum EdgeType {
    REGULAR(1),
    EXCEPTION(2),
    REGULAR_EXCEPTION(1 | 2),
    BREAK(4),
    CONTINUE(8),
    CONTINUE_BREAK(4|8),
    FINALLY_EXIT(32),
    ALL(0x80000000),
    DIRECT_ALL(0x40000000),
    NULL(-1);

    private final int mask;

    EdgeType(int mask) {
      this.mask = mask;
    }

    public int mask() {
      return mask;
    }

    public static EdgeType[] types() {
      return new EdgeType[]{REGULAR, EXCEPTION, BREAK, CONTINUE, FINALLY_EXIT};
    }
  }

  /**
   * Represents a direction of edge.
   * Backward for input edges, forward for output edges.
   */
  public enum EdgeDirection {
    BACKWARD,
    FORWARD
  }
}
