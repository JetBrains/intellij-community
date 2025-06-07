// Inheritance.
class Inheritance {

    /**
     * Parent class description.
     */
    open class InheritanceA {
        /**
         * Parent property description.
         */
        open var inheritanceB = 0

        /**
         * Parent function description.
         */
        open fun inheritanceC() = 0
    }

    class InheritanceD : InheritanceA() {
        override var inheritanceB: Int = 1
        override fun inheritanceC(): Int =1
    }

    /**
     * Child class description.
     */
    class <caret>InheritanceE : InheritanceA() {
        /**
         * Child property description.
         */
        override var inheritanceB: Int = 1

        /**
         * Child function description.
         */
        override fun inheritanceC(): Int =1
    }
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">InheritanceE</span> : <span style="color:#000000;"><a href="psi_element://Inheritance">Inheritance</a></span><span style="">.</span><span style="color:#000000;"><a href="psi_element://Inheritance.InheritanceA">InheritanceA</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Child class description.</p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://Inheritance"><code><span style="color:#000000;">Inheritance</span></code></a><br/></div>
