package testData.libraries

interface WithFunction<in E> {
    fun foo(element: E)
}

interface AScope {}