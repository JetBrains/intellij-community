// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  /**
   * Type of the edges between statements.
   * @see Statement
   */
  public interface EdgeType {
    EdgeType REGULAR = new EdgeType() {
      @Override
      public int mask() {
        return 1;
      }

      @Override
      public String toString() {
        return "REGULAR";
      }
    };

    EdgeType EXCEPTION = new EdgeType() {
      @Override
      public int mask() {
        return 2;
      }

      @Override
      public String toString() {
        return "EXCEPTION";
      }
    };

    EdgeType BREAK = new EdgeType() {
      @Override
      public int mask() {
        return 4;
      }

      @Override
      public String toString() {
        return "BREAK";
      }
    };

    EdgeType CONTINUE = new EdgeType() {
      @Override
      public int mask() {
        return 8;
      }

      @Override
      public String toString() {
        return "CONTINUE";
      }
    };

    EdgeType FINALLY_EXIT = new EdgeType() {
      @Override
      public int mask() {
        return 32;
      }

      @Override
      public String toString() {
        return "FINALLY_EXIT";
      }
    };

    EdgeType ALL = new EdgeType() {
      @Override
      public int mask() {
        return 0x80000000;
      }

      @Override
      public @NotNull EdgeType unite(@NotNull EdgeType other) {
        return this;
      }

      @Override
      public String toString() {
        return "ALL";
      }
    };

    EdgeType DIRECT_ALL = new EdgeType() {
      @Override
      public int mask() {
        return 0x40000000;
      }

      @Override
      public @NotNull EdgeType unite(@NotNull EdgeType other) {
        return this;
      }

      @Override
      public String toString() {
        return "DIRECT_ALL";
      }
    };

    EdgeType NULL = new EdgeType() {
      @Override
      public int mask() {
        return -1;
      }

      @Override
      public @NotNull EdgeType unite(@NotNull EdgeType other) {
        throw new UnsupportedOperationException("Union operation is not supported for NULL edge type");
      }
    };

    int mask();

    default @NotNull EdgeType unite(@NotNull EdgeType other) {
      return new EdgeType() {
        @Override
        public int mask() {
          return EdgeType.this.mask() | other.mask();
        }
      };
    }

    static EdgeType[] types() {
      return new EdgeType[] {REGULAR, EXCEPTION, BREAK, CONTINUE, FINALLY_EXIT};
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
