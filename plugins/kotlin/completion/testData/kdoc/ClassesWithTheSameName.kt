// FIR_COMPARISON
import java.util.regex.MatchResult
import kotlin.text.*

class MatchResult
/**
 * [MatchR<caret>]
 */
fun test() {}

// WITH_ORDER
// EXIST: {"lookupString":"MatchResult","tailText":" (<root>)"}
// EXIST: {"lookupString":"MatchResult","tailText":" (java.util.regex)"}
// EXIST: {"lookupString":"MatchResult","tailText":" (kotlin.text)"}
