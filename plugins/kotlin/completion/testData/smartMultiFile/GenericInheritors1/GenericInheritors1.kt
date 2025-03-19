import p.*

fun foo(): KotlinInterface<I1, I2> {
    return <caret>
}

// EXIST: { lookupString: "object", itemText: "object : KotlinInterface<I1, I2>{...}" }
// EXIST: { lookupString: "KotlinInheritor1", itemText: "KotlinInheritor1", tailText: "() (p)" }
// EXIST: { lookupString: "KotlinInheritor2", itemText: "KotlinInheritor2", tailText: "() (p)" }
// ABSENT: KotlinInheritor3
// EXIST: { lookupString: "object", itemText: "object : KotlinInheritor4<I1, I2>(){...}" }
// ABSENT: KotlinInheritor5
// EXIST: { lookupString: "KotlinInheritor6", itemText: "KotlinInheritor6", tailText: "() (p)" }
// EXIST: { lookupString: "JavaInheritor1", itemText: "JavaInheritor1", tailText: "() (<root>)" }
// ABSENT: JavaInheritor2
