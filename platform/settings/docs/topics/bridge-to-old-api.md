# Seamless Support for PersistenceStateComponent

The new Settings Controller API is currently not intended for use by end clients. 
All existing implementations of `PersistenceStateComponent` that don't use the deprecated API `JDOMExternalizable` are fully supported, and no changes are required.
Support here means that each component property serves as a key, not the whole component.

Each field of a state class is represented by a key. The value is stored in a unified format (see below), rather than in XML as found in regular storage files.

```xml
<component name="TextDiffSettings">
  <option name="SHARED_SETTINGS">
    <SharedSettings>
      <option name="CONTEXT_RANGE" value="8"/>
      <option name="ENABLE_ALIGNING_CHANGES_MODE" value="false"/>
      <option name="MERGE_AUTO_APPLY" value="true"/>
      <option name="MERGE_LIST_GUTTER_MARKERS" value="false"/>
    </SharedSettings>
  </option>
</component>
```

Only top-level fields serve as keys. In this example, `TextDiffSettings.SHARED_SETTINGS` is the key.
What if you want to control only `CONTEXT_RANGE`? To avoid a complicated API, nested beans are not supported. 
That's why a unified format was introduced. This format allows you to work with values in a uniform way using the convenient [kotlinx serialization API](https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json-element/).

The XML value above in unified format will be a JSON:

```json
{
  "shared_settings": {
    "context_range": 8,
    "enable_aligning_changes_mode": false,
    "merge_auto_apply": true,
    "merge_list_gutter_markers": true
  }
}
```

A unified format provides a robust and straightforward method to decompose values and manage sub-values, if necessary.
This approach helps avoid dealing with multiple representations of the same data.

## Unified Format

The Settings Controller operates with values in a unified format, where each value is a [JsonElement](https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json-element/).
Please note that the corresponding API is advanced and experimental, and the `JsonElement` API may be changed in the future.

For primitive values (no binding or binding plus converter, meaning effectively is a string): `"a string"`, `number`, `boolean`.
In other words, a string is quoted with double quotes and other primitive values as is (JSON syntax).

For complex values (binding exists), like 
* map (`MapBinding`), 
* collection (`CollectionBinding`),
* bean (`BeanBinding` and `KotlinAwareBeanBinding`, about `KotlinxSerializationBinding` see note about custom bindings), 

also a JSON syntax is used. 

### Maps

```json
{
  "mapKey": "primitiveValue"
}
```

If a map has a complex key, then it is an array of objects, with each object representing a single key-value pair: 

```json
[
  {
    "key": {},
    "value": "someValue"
  },
  {
    "key2": {}, 
    "value": "someValue"
  }
]
```

Maps with non-primitive keys are expected to be rare.
Currently, `SettingDescriptor` lacks tags which could be used to distinguish formats or provide format details.
These could be added if necessary.

### Collections

```json
[
  "primitive",
  true,
  {
    "complexFieldOfComplexArrayItem": "",
    "complexFieldOfComplexArrayItem2": "" 
  }
]
```

For polymorphic collections, a special key `_class` is used as a class discriminator.

```json
{
  "v": [
    {
      "_class": "BeanWithPublicFields",
      "int_v": 1,
      "string_v": "hello"
    },
    {
      "_class": "BeanWithPublicFieldsDescendant",
      "new_s": "foo",
      "int_v": 1,
      "string_v": "hello"
    },
    {
      "_class": "BeanWithPublicFields",
      "int_v": 1,
      "string_v": "hello"
    }
  ]
}
```

### Custom Binding

A value for a custom binding is a black box, yet it remains a valid JSON.

* `KotlinxSerializationBinding` is currently serialized as JSON using a kotlinx serialization framework. This binding is experimental and has never been recommended for non-cache data.
* `JDOMElementBinding` is serialized as a JSON. It is deprecated and is expected to be rare.

## Under the Hood

How is it possible, and how is it implemented? To understand the explanation, there are several key things you need to know:

* Historically, the IntelliJ Platform uses its own serialization framework.
* The `PersistenceStateComponent` has always been recommended to be implemented using state classes, rather than directly using XML DOM.

Thus, if you have XML data and a state class, you can employ the XML serialization framework to interact with the object at a property level.

You can find example in [StateStorageBackedByController](https://github.com/JetBrains/intellij-community/blob/master/platform/settings-local/src/com/intellij/platform/settings/local/StateStorageBackedByController.kt).

In a nutshell:

* State class it is a [BeanBinding](https://github.com/JetBrains/intellij-community/blob/master/platform/util/src/com/intellij/util/xmlb/BeanBinding.kt).
* Each bean binding consists of a list of [NestedBinding](https://github.com/JetBrains/intellij-community/blob/master/platform/util/src/com/intellij/util/xmlb/Binding.kt). It is a property.
  * `BeanBinding.serializeProperty` to serialize. 
  * `BeanBinding.deserializeInto` to deserialize. 

Please note that this API is internal and low-level. It requires a profound understanding of the IntelliJ Platform. It may not be as straightforward as it sounds. 
Therefore, its use outside the IJ Platform is strongly discouraged.