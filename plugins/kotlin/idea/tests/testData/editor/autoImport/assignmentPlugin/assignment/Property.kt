package assignment

@SupportsKotlinAssignmentOverloading
interface Property<T> {
    set(value: T)
}

fun <T> Property<T>.assign(value: T?)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class SupportsKotlinAssignmentOverloading