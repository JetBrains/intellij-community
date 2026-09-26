class ClassBeforeCompanion {
    class Nested {
    }

    companion {}
}

class ObjectBeforeCompanion {
    object Nested {
    }

    companion {}
}

class CompanionBeforeClass {
    companion {
    }

    class Nested {}
}

class CompanionBeforeObject {
    companion {
    }

    object Nested {}
}

class ConsecutiveCompanions {
    companion {
    }

    companion {}
}
