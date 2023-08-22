import p.*

fun foo(): KotlinInterface<I1, I1> {
    return <caret>
}

// EXIST: { lookupString: "object", itemText: "object : KotlinInterface<I1, I1>{...}" }
// EXIST: { lookupString: "KotlinInheritor", itemText: "KotlinInheritor", tailText: "() (p)" }
