// from https://docs.gradle.org/current/userguide/declaring_dependencies_basics.html
runtimeOnly(group = "org.springframework", name = "spring-core", version = "2.5")
runtimeOnly("org.springframework:spring-core:2.5")
runtimeOnly("org.springframework:spring-aop:2.5")
runtimeOnly(group = "org.springframework", name = "spring-core", version = "2.5")
runtimeOnly(group = "org.springframework", name = "spring-aop", version = "2.5")
runtimeOnly("org.hibernate:hibernate:3.0.5") { isTransitive = true }
runtimeOnly(group = "org.hibernate", name = "hibernate", version = "3.0.5") { isTransitive = true }
runtimeOnly(group = "org.hibernate", name = "hibernate", version = "3.0.5") { isTransitive = true }
