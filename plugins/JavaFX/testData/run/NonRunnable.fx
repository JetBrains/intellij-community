import javafx.scene.text.Text;
import javafx.scene.text.Font;

function foo(): String {
    return "foo";
}

javafx.scene.Scene {
    content: [
        Text {
            x: 10, y: 30
            content: "HelloWorld"
        }
    ]
}