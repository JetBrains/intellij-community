// FIR_COMPARISON
// REGISTRY: kotlin.k2.smart.completion.enabled true
import java.lang.annotation.ElementType

fun foo() {
  val e: ElementType = <caret>
}

// EXIST: { lookupString:"TYPE", itemText:"ElementType.TYPE", typeText:"ElementType" }
// EXIST: { lookupString:"FIELD", itemText:"ElementType.FIELD", typeText:"ElementType" }
