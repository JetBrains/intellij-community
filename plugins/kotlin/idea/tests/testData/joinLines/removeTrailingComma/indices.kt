// AFTER_ERROR: Unresolved reference: g
// K2_AFTER_ERROR: Unresolved reference 'g'.
fun a() {
<caret>    g[1, 2, 3,/**/
    ]
}