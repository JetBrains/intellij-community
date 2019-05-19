// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.annotations;

import java.lang.annotation.*;

/**
 * @author nik
 */
public final class ApiStatus {
  /**
   * Indicates that a public API of the annotated element (class, method or field) is not in stable state yet. It may be renamed, changed or
   * even removed in a future version. This annotation refers to API status only, it doesn't mean that the implementation has
   * an 'experimental' quality.
   * <p/>
   * It's safe to use an element marked with this annotation if the usage is located in the same sources codebase as the declaration. However
   * if the declaration belongs to an external library such usages may lead to problems when the library will be updated to another version.
   */
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({
    ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE
  })
  public @interface Experimental {}

  /**
   * Indicates that the annotated element (class, method, field, etc) must not be considered as a public API. It's made visible to allow
   * usages in other parts of the declaring library but it must not be used outside that library. Such elements
   * may be renamed, changed or removed in future versions.
   */
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({
    ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE
  })
  public @interface Internal {}

  /**
   * Indicates that a public API of the annotated element (class, method or field) is subject to removal in a future version. It's a stronger
   * variant of {@link Deprecated} annotation.
   * <br>
   * Since many tools aren't aware of this annotation it should be used as an addition to {@code @Deprecated} annotation or {@code @deprecated} javadoc tag only.
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
   * Indicates that the annotated element firstly appeared in the specified version of the library, so the code which uses that element
   * won't be compatible with older versions of the library. This information may be used by IDEs and static analysis tools.
   * <br>
   * This annotation can be used instead of '@since' javadoc tag if it's needed to keep that information in *.class files or if you need
   * to generate them automatically.
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
     * Specifies version where the annotation API firstly appeared.
     */
    String value();
  }

  /**
   * Indicates that the annotated API class, interface or method <strong>must not be extended, implemented or overridden</strong>.
   * <p/>
   * API class, interface or method may not be marked {@code final} because it is extended by classes of the declaring library
   * but it is not supposed to be extended outside the library.
   * <br/>
   * Instances of classes and interfaces marked with this annotation may be cast to an internal implementing class in the library code,
   * leading to {@code ClassCastException} if a different implementation is provided by client.
   * <br/>
   * New abstract methods may be added to such classes and interfaces in new versions of the library breaking compatibility
   * with client's implementations.
   */
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({
    ElementType.TYPE, ElementType.METHOD
  })
  public @interface NonExtendable { }

  /**
   * Indicates that the annotated method is part of SPI (Service Provider Interface), which is intended to be
   * <strong>only implemented or overridden</strong> but not called by clients of the declaring library.
   * If a class or interface is marked with this annotation it means that all its methods can be only overridden.
   * <p/>
   * Although there is a standard mechanism of <code>protected</code> methods, it is not applicable to interface's methods.
   * Also API method may be made <code>public</code> to allow calls only from different parts of the declaring library but not outside it.
   * <br/>
   * Signatures of such methods may be changed in new versions of the library in the following steps. Firstly, a method with new signature
   * is added to the library delegating to the old method by default. Secondly, all clients implement the new method and remove
   * implementations of the old one. This leads to compatibility breakage with code that calls the methods directly.
   */
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({
    ElementType.TYPE, ElementType.METHOD
  })
  public @interface OverrideOnly { }
}
