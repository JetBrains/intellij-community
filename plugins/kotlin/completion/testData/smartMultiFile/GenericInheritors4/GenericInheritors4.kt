import p.*

fun foo(): KotlinInterface<I1<I2>> {
    return <caret>
}

// EXIST: { lookupString: "object", itemText: "object : KotlinInterface<I1<I2>>{...}" }
// EXIST: { lookupString: "KotlinInheritor", itemText: "KotlinInheritor", tailText: "() (p)" }
