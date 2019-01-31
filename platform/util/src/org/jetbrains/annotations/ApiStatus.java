// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.annotations;

import java.lang.annotation.*;

/**
 * @author nik
 */
public class ApiStatus {
  /**
   * Indicates that a public API of the annotated element (class, method or field) is not in stable state yet. It may be renamed, changed or
   * even removed in a future version. This annotation refers to API status only, it doesn't mean that the implementation has
   * an 'experimental' quality.
   * <p/>
   * It's safe to use an element marked by this annotation if the usage is located in the same sources codebase as the declaration. However
   * if the declaration belongs to an external library such usages may lead to problems when the library will be updated to another version.
   */
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({
    ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE
  })
  public @interface Experimental {}

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
}
