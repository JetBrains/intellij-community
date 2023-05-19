import com.intellij.openapi.actionSystem.AnAction;

class A extends B {
  <warning descr="Action registered in plugin.xml should not initialize the presentation (text, description, and/or icon) inits ctor. Instead, use no-argument constructors of 'AnAction' and other base classes and followthe convention for setting the text, description, and icon:
Set the 'id' attribute for the action in plugin.xml.If an icon is needed, optionally set the icon attribute for the action in plugin.xml.Follow this structure when setting the keys for text and description in the property file:
In the property file, specify the text key as 'action.<action-id>.text=Translated Action Text.'In the property file, specify the description key as 'action.<action-id>.description=Translated Action Description.'">A</warning>() {
    this(42);
  }

  <warning descr="Action registered in plugin.xml should not initialize the presentation (text, description, and/or icon) inits ctor. Instead, use no-argument constructors of 'AnAction' and other base classes and followthe convention for setting the text, description, and icon:
Set the 'id' attribute for the action in plugin.xml.If an icon is needed, optionally set the icon attribute for the action in plugin.xml.Follow this structure when setting the keys for text and description in the property file:
In the property file, specify the text key as 'action.<action-id>.text=Translated Action Text.'In the property file, specify the description key as 'action.<action-id>.description=Translated Action Description.'">A</warning>(int i) {
  }
}

class B extends AnAction {
  B() {
    super("");
  }
}