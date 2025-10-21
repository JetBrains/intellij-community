@Target( AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
@Retention
annotation class Responses(vararg val value: Response)

@Target( AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
@Retention
annotation class Response(vararg val content: String)

class Example <fold text='{...}' expand='true'>{
    @Responses<fold text='{...}' expand='true'>(
        Response("single"),
        Response<fold text='(...)' expand='true'>(
            "multi",
            "line",
            "content"
        )</fold>,
    )</fold>
    fun foo() {}
}</fold>
