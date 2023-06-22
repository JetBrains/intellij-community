plugins {
    kotlin("multiplatform")
}

val attr = Attribute.of("disambiguity.attr", String::class.java)

kotlin {
    iosArm64("iosArm64V1") {
        attributes.attribute(attr, "v1")
    }

    iosArm64("iosArm64V2") {
        attributes.attribute(attr, "v2")
    }

    targetHierarchy.custom {
        common {
            group("sharedNative") {
                withIosArm64()
            }
        }
    }
}
