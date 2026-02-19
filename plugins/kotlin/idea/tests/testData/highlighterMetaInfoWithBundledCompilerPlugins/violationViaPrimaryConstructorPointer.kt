// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/serialize_fake_plugin.jar
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
// FILE: main.kt
package foo

fun foo(): JavaClass.NestedClass? {
    return null
}

// FILE: JavaClass.java

import static NlsContexts.*;
import static NlsContexts.foo;
import static NlsContexts.NlsContexts;
import java.util.List;
import NlsContexts;

public class JavaClass {
    @NlsContexts.DialogTitle
    public kotlin.@NlsContexts.DialogTitle2 List<NlsContexts.@NlsContexts.DialogTitle DialogTitle3> bar() {

    }

    public static class NestedClass {

    }
}

// FILE: NlsContext.kt
import org.jetbrains.annotations.NonNls

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class NlsContext(
    /**
     * Provide a neat property key prefix that unambiguously defines literal usage context.
     * E.g. "button", "button.tooltip" for button text and tooltip correspondingly, "action.text" for action text
     */
    val prefix: @NonNls String = "",

    /**
     * Provide a neat property key suffix that unambiguously defines literal usage context.
     * E.g. "description" for action/intention description
     */
    val suffix: @NonNls String = "",
)

// FILE: NlsContexts.kt
import org.jetbrains.annotations.Nls
import kotlin.annotation.AnnotationTarget.*

class NlsContexts {
    /**
     * Dialogs
     */
    @NlsContext(prefix = "dialog.title")
    @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, FIELD)
    @Nls(capitalization = Nls.Capitalization.Title)
    annotation class DialogTitle

    /**
     * Dialogs
     */
    @NlsContext(prefix = "dialog.title")
    @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, FIELD)
    @Nls(capitalization = Nls.Capitalization.Title)
    annotation class DialogTitle2

    /**
     * Dialogs
     */
    @NlsContext(prefix = "dialog.title")
    @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, FIELD)
    @Nls(capitalization = Nls.Capitalization.Title)
    annotation class DialogTitle3
    fun DialogTitle3() {}
}
