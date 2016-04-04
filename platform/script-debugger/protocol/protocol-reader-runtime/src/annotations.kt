/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jsonProtocol

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
annotation class JsonField(
  val allowAnyPrimitiveValue: Boolean = false, // read any primitive value as String (true as true, number as string - don't try to parse)
  val allowAnyPrimitiveValueAndMap: Boolean = false,
  val primitiveValue: String = "")

@Target(AnnotationTarget.CLASS)
annotation class JsonType()

@Target(AnnotationTarget.FUNCTION)
annotation class JsonSubtypeCasting(val reinterpret: Boolean = false)

@Target(AnnotationTarget.FUNCTION)
annotation class JsonParseMethod

/**
 * For field-reading method specifies that the field is optional and may safely be absent in
 * JSON object. By default fields are not optional.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
annotation class Optional(val default: String = "")

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
annotation class ProtocolName(val name: String)