// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.annotations;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts access to an API element to the JPS module that declares it and to an explicitly listed set of
 * <em>friend</em> classes, similar to the {@code friend} mechanism in C++ or {@code [InternalsVisibleTo]} in C#.
 * <p>
 * Access rules enforced by the Monorepo DevKit inspection:
 * <ul>
 *   <li>The annotated element may always be used from the JPS module where it is declared, regardless of the list: the
 *   annotation implies the visibility of Kotlin {@code internal}. There is no way to narrow the access down to specific
 *   classes of the declaring module.</li>
 *   <li>The annotated element may be used from every class listed in {@link #value()}, no matter which module hosts that
 *   class, including the code nested inside such a class: its nested and inner classes, anonymous classes, lambdas and
 *   companion objects.</li>
 *   <li>Any other usage is reported as an error.</li>
 * </ul>
 * Every entry of {@link #value()} is a fully qualified class name. Wildcards, globs and regular expressions are not supported.
 * <ul>
 *   <li>A nested class may be spelled either as {@code com.example.Outer.Nested} or as {@code com.example.Outer$Nested}.
 *   Mind that Kotlin requires the dollar sign in a string to be escaped, so the dotted form is usually more convenient
 *   there.</li>
 *   <li>Listing an outer class grants access to the classes nested inside it, but listing a nested class grants no access to
 *   the outer one.</li>
 *   <li>A Kotlin declaration on the top level of a file belongs to the JVM facade class of that file: {@code Foo.kt} declares
 *   {@code FooKt}, and {@code @file:JvmName("Bar")} renames the facade class to {@code Bar}. Files annotated with
 *   {@code @file:JvmMultifileClass} are not supported.</li>
 * </ul>
 * The list must be non-empty, and every listed class must exist in the project.
 * <p>
 * <h2>Visibility of the annotated element
 * The annotation is applicable to every element that more than one class can reach:
 * <ul>
 *   <li>{@code public} and {@code protected} elements must always accompany this annotation with {@link ApiStatus.Internal}:
 *   the {@link ApiStatus.Internal} marker may be placed on the element itself, on one of its containing classes, or in the
 *   package {@code package-info}.</li>
 *   <li>Kotlin {@code internal} elements and Java package-private elements may be annotated as they are.
 *   {@link ApiStatus.Internal} is not applicable to such elements, hence it is not required. Mind that such an element is
 *   restricted to its own module by the compiler anyway, so the list only matters for a class that still reaches it from
 *   the outside, i.e. from a split package or from a Kotlin friend module.</li>
 *   <li>{@code private} elements may not be annotated: everything that can access a {@code private} element is already in the
 *   same file, hence in the declaring module, so any list of friend classes there would be dead weight.</li>
 * </ul>
 * The visibility of an element includes the visibility of its containing classes, i.e. a {@code public} method of a Kotlin
 * {@code internal} class is treated as {@code internal}.
 * <p>
 * An annotation on a method overrides the effect of the annotation on a class level. The same applies to other contextual
 * relations like method-package, class-package.
 * <p>
 * <h2>Examples
 * <h3>Direct usage
 * The classes {@code com.example.Foo}, {@code com.example.Bar} and {@code com.example.Other} below belong to modules
 * other than the one that declares {@code Example}, because a usage from the declaring module is never reported.
 * <pre>{@code
 *   @ApiStatus.Internal
 *   @VisibleToClasses("com.example.Foo", "com.example.Bar")
 *   public static class Example {
 *     public static void one() {}
 *     @VisibleToClasses("com.example.Bar") public static void two() {}
 *   }
 *
 *   // com.example.Foo
 *   Example.one()  // OK
 *   Example.two()  // Fail
 *
 *   // com.example.Bar
 *   Example.one()  // OK
 *   Example.two()  // OK
 *
 *   // com.example.Other
 *   Example.one()  // Fail
 *   Example.two()  // Fail
 * }</pre>
 *
 * <h3>Kotlin top level declarations
 * <pre>{@code
 * // com/example/utils.kt
 * @ApiStatus.Internal
 * @VisibleToClasses("com.example.ConsumerKt")  // the facade class of com/example/consumer.kt, in another module
 * fun helper(): Unit = Unit
 * }</pre>
 *
 * <h3>Nested annotation
 * <pre>{@code
 * @ApiStatus.Internal
 * @VisibleToClasses("com.example.One", "com.example.Two", "com.example.N")
 * public @interface MyClassesOnly {}
 *
 * @ApiStatus.Internal
 * @MyClassesOnly
 * public class Example {}
 *
 * }</pre>
 * When several annotations are placed on the <em>same</em> element, their friend lists are <em>merged</em> (unioned).
 * This includes multiple alias annotations, so the element becomes visible from the classes of every alias. For example:
 * <pre>{@code
 * @ApiStatus.Internal
 * @VisibleToClasses("com.example.A")
 * public @interface OnlyA {}
 *
 * @ApiStatus.Internal
 * @VisibleToClasses("com.example.B")
 * public @interface OnlyB {}
 *
 * @ApiStatus.Internal
 * @OnlyA
 * @OnlyB
 * public class Example {}  // visible from `com.example.A` and `com.example.B`
 * }</pre>
 * Note the difference from the override rule above: multiple annotations on the <em>same</em> element are merged, while an
 * annotation on a more specific level (e.g. a method) overrides the annotation on a less specific level (e.g. its class).
 * Only a single level of alias indirection is resolved &mdash; an alias whose target is itself only an alias does not chain.
 *
 * @see ApiStatus.Internal
 */
@ApiStatus.Internal
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE})
public @interface VisibleToClasses {
  /**
   * @return the fully qualified names of the classes that are allowed to access the annotated element, in addition to the
   * JPS module where the element is declared. Must be non-empty, and every name must correspond to a class in the project.
   */
  @NotNull String @NotNull [] value();
}
