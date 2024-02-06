# J2K

## JK Tree and JK Tree Conversions

JK Tree is a mutable syntax tree that represents converted code from Java to Kotlin. Initially, it's built from Java PSI. Afterward, it's
handled by a sequence of JK Tree conversions (`org.jetbrains.kotlin.nj2k.Conversion`). Every JK Tree conversion may mutate the tree. The
goal of the entire conversion pipeline is to convert Java structures in JK Tree to corresponding Kotlin structures.

## JKElement

`JKElement` is a mutable syntax tree node. Every node:

- May have some children.
- Has a single parent node (except for the `JKTreeRoot`, which has no parent node)
- May have information about a Java `PsiElement` it was initially created from.
  It's stored in `org.jetbrains.kotlin.nj2k.tree.PsiOwner.psi`. When converting a `JKElement`, the `PsiElement` information is usually
  preserved.
- Has information about formatting stored in `org.jetbrains.kotlin.nj2k.tree.JKFormattingOwner`. This information consists of comments and
  line breaks before and after an element. The formatting is usually preserved between conversions.

### JKElement Parenting

As `JKElement` is mutable, we want to avoid situations where the same `JKElement` is attached to multiple parents to avoid accidentally
mutating a node in unexpected places. To guard against such situations, every JK Tree node is aware of all its children and its parent.
To reattach some element to another parent, this element should first be detached by `JKElement.detach` or `JKElement.detached` functions.
After that, this element may be assigned as a child to a new parent. If a `JKElement` is not needed, it may be invalidated;
see `JKTreeElement.invalidate`.
This will detach all direct children from the element. Nested child elements (child elements of child elements) are kept intact.
After that, all direct children may be attached to some new parent.

There is also a `copyTreeAndDetach` method which creates a fresh new recursive copy of a `JKElement`. The method `copyTreeAndDetach` should
be avoided as much as possible because it recursively copies the entire `JKElement` node.
In a worst-case scenario, if it's called from a conversion each time, it can result in exponential complexity.
It's recommended to use it only on simple nodes like the `JKNameIdentifier`, or when a genuine copy of a node is needed, e.g., when creating
a full copy of a method to place it near the original method.

### JKExpression

Represents an expression in a code. An expression is a node which has a type represented by `JKType`. Such type can be calculated, e.g., by
performing a type substitution by a method call or it can be predefined in `JKExpression.expressionType`. When the type of an expression is
known, it's always better to explicitly provide it on `JKExpression` instance creation, which will help with the performance.

### JKElement Conversions

JKElement conversions are represented as `org.jetbrains.kotlin.nj2k.Conversion`.
`Conversion.applyToElement` is a function which accepts a `JKElement` and may modify the tree, recursively traversing its children.

#### Traversing JKTreeElement's children during conversion

For traversing the children, there is a `RecursiveConversion.recurse` function.
It delves into the element's direct children, applying the conversion to them recursively.

So, there are two ways of handling children:

```kotlin
class SomeConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        // 1. Recursively apply the conversion to all direct children of the `element`.
        return recurse(element)
        // 2. Do not apply the conversion to the element's children. This means that neither the `element`'s direct children nor the nested ones will be converted
        return element
    }
}
```

#### Approaches for implementing a `org.jetbrains.kotlin.nj2k.Conversion`:

##### Creating a new `JKElement`

In this case, the parent reference to the new node will be replaced with the new one returned from `Conversion.applyToElement`.

```kotlin
class SomeConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        val newElement = ... // Create a new `JKTreeElement` which will be the result of the conversion for the `element`
        return recurse(newElement) // Apply conversion to all child elements
    }
}
```

##### Modifying the `JKElement`'s children directly inside an `applyToElement` implementation

```kotlin
class SomeConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        // Transform the children of the `element`
        return recurse(element) // Apply conversion to all child elements.
    }
}
```