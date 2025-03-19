testImplementation(group = "org.springframework", name = "spring-core", version = "2.5") { isTransitive = true }
testApi(group = "org.springframework", name = "spring-core", version = "2.5") { isTransitive = true }
testImplementation(group = "org.springframework", name = "spring-core", version = "2.5") { isTransitive = false }
testApi(group = "org.springframework", name = "spring-core", version = "2.5") { isTransitive = false }
