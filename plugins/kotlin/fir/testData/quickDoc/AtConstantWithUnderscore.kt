class C {
    /** Use [SOME_REFERENCED_VAL] to do something */
    fun fo<caret>o() {

    }

    companion object {
        val SOME_REFERENCED_VAL = 1
    }
}

//INFO: <div class='definition'><pre>fun foo(): Unit</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Use <a href="psi_element://SOME_REFERENCED_VAL"><code style='font-size:96%;'><span style="color:#660e7a;font-weight:bold;">SOME_REFERENCED_VAL</span></code></a> to do something</p></div><table class='sections'></table><div class='bottom'><icon src="class"/>&nbsp;<a href="psi_element://C"><code><span style="color:#000000;">C</span></code></a><br/></div>
