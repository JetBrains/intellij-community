import com.intellij.ide.presentation.Presentation;

@Presentation(icon = "<error descr="Cannot resolve icon 'InvalidIconPath'">InvalidIconPath</error>")
class InvalidIcon {
}

@Presentation(icon = "<error descr="Cannot resolve icon ''"></error>")
class EmptyIcon {
}