// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.typeann;

/**
 * Indicates the location of type annotations, retrieved from the type annotation attribute.
 */
public interface TargetInfo {
  /**
   * An item indicating that an annotation appears on the i'th type in an exception parameter declaration.
   */
  class CatchTarget implements TargetInfo {
    private final int exceptionTableIndex;

    public CatchTarget(int exceptionTableIndex) {
      this.exceptionTableIndex = exceptionTableIndex;
    }

    /**
     * @return An index indicating an item into the exception table of the code attribute.
     */
    public int getExceptionTableIndex() {
      return exceptionTableIndex;
    }
  }

  /**
   * An item indicating that an annotation appears on either the type in a field declaration, the type in a record component declaration,
   * the return type of a method, the type of a newly constructed object, or the receiver type of a method or constructor.
   */
  class EmptyTarget implements TargetInfo { }

  /**
   * An item indicating that an annotation appears on the type in a formal parameter declaration of a method, constructor, or
   * lambda expression.
   */
  class FormalParameterTarget implements TargetInfo {
    private final int formalParameterIndex;

    public FormalParameterTarget(int formalParameterIndex) {
      this.formalParameterIndex = formalParameterIndex;
    }

    /**
     * @return An index indicating which formal parameter declaration has an annotated type. The index starts at 0.
     */
    public int getFormalParameterIndex() {
      return formalParameterIndex;
    }
  }

  /**
   * An item indicating that an annotation appears on the type in a local variable declaration, including a variable declared as a resource
   * in a try-with-resources statement.
   */
  class LocalvarTarget implements TargetInfo {
    private final Offsets[] table;

    public LocalvarTarget(Offsets[] table) {
      this.table = table;
    }

    /**
     * @return A table containing the offsets of all local variables.
     */
    public Offsets[] getTable() {
      return table;
    }

    /**
     * Offset entry in the local var table.
     */
    public static class Offsets {
      private final int startPc;

      private final int length;

      private final int index;

      public Offsets(int startPc, int length, int index) {
        this.startPc = startPc;
        this.length = length;
        this.index = index;
      }

      /**
       * @return The start of the code array interval.
       */
      public int getStartPc() {
        return startPc;
      }

      /**
       * @return The length array of the code array interval.
       */
      public int getLength() {
        return length;
      }

      /**
       * @return The given local variable must be at index in the local variable array of the current frame.
       */
      public int getIndex() {
        return index;
      }
    }
  }

  /**
   * Item that indicates that an annotation appears on either the type in an instanceof expression or a new expression, or the type before
   * the :: in a method reference expression.
   */
  class OffsetTarget implements TargetInfo {
    private final int offset;

    public OffsetTarget(int offset) {
      this.offset = offset;
    }

    /**
     * @return The offset item, specifying the code array offset of either the bytecode instruction corresponding to the instanceof
     * expression, the new bytecode instruction corresponding to the new expression, or the bytecode instruction corresponding to
     * the method reference expression.
     */
    public int getOffset() {
      return offset;
    }
  }

  /**
   * Item that indicates that an annotation appears on a type in the extends or implements clause of a class or interface declaration. The
   * index starts at 0.
   */
  class SupertypeTarget implements TargetInfo {
    private final int supertypeIndex;

    public SupertypeTarget(int supertypeIndex) {
      this.supertypeIndex = supertypeIndex;
    }

    /**
     * @return An index into the interfaces array of the enclosing ClassFile structure, which specifies that the annotation appears on that
     * superinterface in either the implements clause of a class declaration or the extends clause of an interface declaration. A value of
     * 65535 specifies that the annotation appears on the superclass in an extends clause of a class declaration.
     */
    public int getSupertypeIndex() {
      return supertypeIndex;
    }
  }

  /**
   * An item indicating that an annotation appears on the i'th type in the throws clause of a method or constructor declaration.
   */
  class ThrowsTarget implements TargetInfo {
    private final int throwsTypeIndex;

    public ThrowsTarget(int throwsTypeIndex) {
      this.throwsTypeIndex = throwsTypeIndex;
    }

    /**
     * @return An index into the exception table array of the exceptions attribute of the method info structure. The index starts at 0.
     */
    public int getThrowsTypeIndex() {
      return throwsTypeIndex;
    }
  }

  /**
   * Item that indicates that an annotation appears on the declaration of the i'th type parameter of a generic class, generic interface,
   * generic method, or generic constructor.
   */
  class TypeParameterTarget implements TargetInfo {
    private final int typeParameterIndex;

    public TypeParameterTarget(int typeParameterIndex) {
      this.typeParameterIndex = typeParameterIndex;
    }

    /**
     * @return an index indicating which parameter is annotated. The index starts at 0.
     */
    public int getTypeParameterIndex() {
      return typeParameterIndex;
    }
  }

  /**
   * Item that indicates that an annotation appears on the i'th bound of the j'th type parameter declaration of a generic class, interface,
   * method, or constructor.
   */
  class TypeParameterBoundTarget implements TargetInfo {
    private final int typeParameterIndex;

    private final int boundIndex;

    public TypeParameterBoundTarget(int typeParameterIndex, int boundIndex) {
      this.typeParameterIndex = typeParameterIndex;
      this.boundIndex = boundIndex;
    }

    /**
     * @return an index indicating which type parameter declaration has an annotated bound. The index starts at 0.
     */
    public int getTypeParameterIndex() {
      return typeParameterIndex;
    }

    /**
     * @return an index indicating which bound of the type parameter declaration indicated by getTypeParameterIndex is annotated.
     */
    public int getBoundIndex() {
      return boundIndex;
    }
  }

  class TypeArgumentTarget implements TargetInfo {
    private final int offset;

    private final int typeArgumentIndex;

    public TypeArgumentTarget(int offset, int typeArgumentIndex) {
      this.offset = offset;
      this.typeArgumentIndex = typeArgumentIndex;
    }

    public int getOffset() {
      return offset;
    }

    public int getTypeArgumentIndex() {
      return typeArgumentIndex;
    }
  }
}
