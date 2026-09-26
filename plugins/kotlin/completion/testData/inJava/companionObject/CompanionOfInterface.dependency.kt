package a

interface ITest {
    companion object {
        fun create(): ITest = object : ITest {}
    }
}
