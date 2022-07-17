// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct;

/**
 * An entry in the type path which indicates the path of a type annotation.
 */
public class StructTypePathEntry {
  private final int typePathEntryKind;
  private final int typeArgumentIndex;

  public StructTypePathEntry(int typePathKind, int typeArgumentIndex) {
    this.typePathEntryKind = typePathKind;
    this.typeArgumentIndex = typeArgumentIndex;
  }

  /**
   * @return The type argument index, indicating which nested type argument is annotated. The index starts at 0.
   */
  public int getTypeArgumentIndex() {
    return typeArgumentIndex;
  }

  /**
   * @return {@link Kind#id} of this type path entry.
   */
  public int getTypePathEntryKind() {
    return typePathEntryKind;
  }

  /**
   * The type_path_kind.
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html">The JVM class File Format Spec</a> Table 4.7.20.2 A
   */
  public enum Kind {
    /**
     *  Type path entry is an array type contained in e.g. <code>@I String[] @G [] @H []</code>
     */
    ARRAY(0),

    /**
     *  Type path entry is a nested type contained in e.g. <code> Outer . @B Middle . @C Inner</code>
     */
    NESTED(1),

    /**
     *  Type path entry is a wildcard contained in e.g. <code>Map<@B ? extends String, String></code>
     */
    TYPE_WILDCARD(2),

    /**
     * Type path entry is a type argument of a parameterized type contained in e.g. <code>List<@B Comparable<@F Object>></code>
     */
    TYPE(3);

    private final int id;

    Kind(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }
  }
}
