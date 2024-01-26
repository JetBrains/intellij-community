# Implicit support for PersistenceStateComponent

The new Settings Controller API is currently not intended for use by end clients. All existing implementations of `PersistenceStateComponent` that don't use the deprecated API like `JDOMExternalizable` are fully supported, and no changes are required. Support here means that each component property serves as a key, not the whole component.

How is it possible, and how is it implemented? To understand the explanation, there are several key things you need to know:

* Historically, the IntelliJ Platform uses its own serialization framework.
* The `PersistenceStateComponent` has always been recommended to be implemented using state classes, rather than directly using XML DOM.

Thus, if you have XML data and a state class, you can employ the XML serialization framework to interact with the object at a property level.

You can find example in [StateStorageBackedByController](https://github.com/JetBrains/intellij-community/blob/master/platform/settings-local/src/com/intellij/platform/settings/local/StateStorageBackedByController.kt).

In a nutshell:

* State class it is a [BeanBinding](https://github.com/JetBrains/intellij-community/blob/master/platform/util/src/com/intellij/util/xmlb/BeanBinding.java).
* Each bean binding consists of a list of [NestedBinding](https://github.com/JetBrains/intellij-community/blob/master/platform/util/src/com/intellij/util/xmlb/NestedBinding.java). It is a property.
  * `BeanBinding.serializePropertyInto` to serialize. 
  * `BeanBinding.deserializeInto` to deserialize. 

Please note that this API is internal and low-level. It requires a profound understanding of the IntelliJ Platform. It may not be as straightforward as it sounds. 
Therefore, its use outside of the IJ Platform is strongly discouraged.