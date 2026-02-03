@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FIELD,
    AnnotationTarget.FILE,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.BINARY)
annotation class AuxAnnA

@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class AuxAnnB

annotation class AuxAnnC

interface AuxFaceA
interface AuxFaceB
interface AuxFaceD<X>
interface AuxFaceE<X, Y>

open class AuxClassA
class AuxClassB {
    open class AuxClassC
    interface AuxFaceC
}
open class AuxClassD(afa: AuxFaceA)

data class AuxDataClassA(val pi: Int, var ps: String)
