// Inheritance.
class Inheritance {

    /**
     * Parent class description.
     */
    open class <caret>InheritanceA {
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
    class InheritanceE : InheritanceA() {
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
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">open</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">InheritanceA</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Parent class description.</p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://Inheritance"><code><span style="color:#000000;">Inheritance</span></code></a><br/></div>
