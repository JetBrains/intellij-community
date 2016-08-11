/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  @Experimental
  @Documented
  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
  @interface Experimental {}
}
