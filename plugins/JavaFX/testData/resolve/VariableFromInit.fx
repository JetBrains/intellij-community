class A {
    var text: Text = Text {
        content: bind "{data}"
        translateX: bind (defRect.width - <ref>text.boundsInLocal.width) / 2.0 + 1.8
    }
}