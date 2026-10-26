fun beforeAfter(items: List<String>) {
    val count = <caret>items.size
}

// K2_TYPE: items -> <b>List&lt;String&gt;</b>
// K2_TYPE: items.size -> <b>Int</b>
