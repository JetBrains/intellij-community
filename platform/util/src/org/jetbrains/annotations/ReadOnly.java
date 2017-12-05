// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.jetbrains.annotations;

import java.lang.annotation.*;

/**
 * An annotation which depicts that method returns a read-only value or a variable
 * contains a read-only value. Read-only value means that calling methods which may
 * mutate this value (alter visible behavior) either don't have any effect or throw
 * an exception. This does not mean that value cannot be altered at all. For example,
 * a value could be a read-only wrapper over a mutable value.
 * <p>
 * This annotation is experimental and may be changed/removed in future
 * without additional notice!
 * </p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
@ApiStatus.Experimental
public @interface ReadOnly {
}