class ClassBeforeCompanion {
    class Nested {}companion {}
}

class ObjectBeforeCompanion {
    object Nested {}companion {}
}

class CompanionBeforeClass {
    companion {}class Nested {}
}

class CompanionBeforeObject {
    companion {}object Nested {}
}

class ConsecutiveCompanions {
    companion {}companion {}
}

class CompanionBeforeFunction {
    companion {}fun foo() {}
}

class CompanionBeforeProperty {
    companion {}val foo = 1
}
