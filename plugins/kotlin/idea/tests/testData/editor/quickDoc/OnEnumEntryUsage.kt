enum class TestEnum{
    A, B, C
}

fun test() {
    TestEnum.<caret>C
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">enum entry</span> <span style="color:#000000;">C</span><br><span style="color:#808080;font-style:italic;">// </span><span style="color:#808080;font-style:italic;">Enum constant ordinal: 2</span></pre></div><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/classKotlin.svg"/>&nbsp;<a href="psi_element://TestEnum"><code><span style="color:#000000;">TestEnum</span></code></a><br/></div>
