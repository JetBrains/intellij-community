// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.annotations;

import java.lang.annotation.*;

/**
 * @author nik
 */
public final class ApiStatus {
  /**
   * <p>Indicates that a public API of the annotated element (class, method or field) is not in stable state yet. It may be renamed, changed or
   * even removed in a future version. This annotation refers to API status only, it doesn't mean that the implementation has
   * an 'experimental' quality.</p>
   *
   * <p>It's safe to use an element marked with this annotation if the usage is located in the same sources codebase as the declaration. However,
   * if the declaration belongs to an external library such usages may lead to problems when the library will be updated to another version.</p>
   */
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({
    ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE
  })
  public @interface Experimental {}

  /**
   * Indicates that the annotated element (class, method, field, etc) must not be considered as a public API. It's made visible to allow
   * usages in other packages of the declaring module, but it must not be used outside of that module. Such elements
   * may be renamed, changed or removed in future versions.
   */
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({
    ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE
  })
  public @interface Internal {}

  /**
   * <p>Indicates that a public API of the annotated element (class, method or field) is subject to removal in a future version.
   * It's a stronger variant of {@link Deprecated} annotation.</p>
   *
   * <p>Since many tools aren't aware of this annotation it should be used as an addition to {@code @Deprecated} annotation
   * or {@code @deprecated} Javadoc tag only.</p>
   */
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({
    ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE
  })
  public @interface ScheduledForRemoval {
    /**
     * Specifies in which version the API will be removed.
     */
    String inVersion() default "";
  }

  /**
   * <p>Indicates that the annotated element firstly appeared in the specified version of the library, so the code using that element
   * won't be compatible with older versions of the library. This information may be used by IDEs and static analysis tools.
   * This annotation can be used instead of '@since' Javadoc tag if it's needed to keep that information in *.class files or if you need
   * to generate them automatically.</p>
   *
   * <p><strong>Do not write this annotation in sources of IntelliJ IDEA project.</strong> These annotations are automatically generated as
   * external annotations for API from IntelliJ IDEA project.</p>
   */
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({
    ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE
  })
  public @interface AvailableSince {
    /**
     * Specifies a version where the annotation API firstly appeared.
     */
    String value();
  }

  /**
   * <p>Indicates that the annotated API class, interface or method <strong>must not be extended, implemented or overridden</strong>.</p>
   *
   * <p>API class, interface or method may not be marked {@code final} because it is extended by classes of the declaring library
   * but it is not supposed to be extended outside the library. Instances of classes and interfaces marked with this annotation
   * may be cast to an internal implementing class in the library code, leading to {@code ClassCastException}
   * if a different implementation is provided by a client.</p>
   *
   * <p>New abstract methods may be added to such classes and interfaces in new versions of the library breaking compatibility
   * with a client's implementations.</p>
   */
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({
    ElementType.TYPE, ElementType.METHOD
  })
  public @interface NonExtendable { }

  /**
   * <p>Indicates that the annotated method is part of SPI (Service Provider Interface), which is intended to be
   * <strong>only implemented or overridden</strong> but not called by clients of the declaring library.
   * If a class or interface is marked with this annotation it means that all its methods can be only overridden.</p>
   *
   * <p>Although there is a standard mechanism of {@code protected} methods, it is not applicable to interface's methods.
   * Also, API method may be made {@code public} to allow calls only from different parts of the declaring library but not outside it.</p>
   *
   * <p>Signatures of such methods may be changed in new versions of the library in the following steps. Firstly, a method with new signature
   * is added to the library delegating to the old method by default. Secondly, all clients implement the new method and remove
   * implementations of the old one. This leads to compatibility breakage with code that calls the methods directly.</p>
   */
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({
    ElementType.TYPE, ElementType.METHOD
  })
  public @interface OverrideOnly { }
}