Please consider to use annotation parameters only to achieve backward compatibility. Otherwise feel free to file issues about serialization cosmetics.

## Lists and Sets

`XCollection` annotation intended to customize list and set serialization.

Two styles are provided:

* `v1`:
  ```xml
  <option name="propertyName">
    <option value="value1" />
    <option value="valueN" />
  </option>
  ```
    
* `v2`:
  ```xml
  <propertyName>
    <option value="$value" />
  </propertyName>
  ```

Where second-level `option` it is item element (use `elementName` to customize element name) and
`value` it is value attribute (use `valueAttributeName` to customize attribute name).

Because of backward compatibility, `v1` style is used by default. In the examples `v2` style is used.

### Custom List Item Value Attribute Name

Value of primitive type wrapped into element named `option`. `valueAttributeName` allows you to customize name of value attribute.

Empty name is allowed — in this case value will be serialized as element text.

* `valueAttributeName = "name"`
  ```xml
  <propertyName>
    <option name="$value1" />
    <option name="$valueN" /> 
  </propertyName>
  ```
* `valueAttributeName = ""`
  ```xml
  <propertyName>
    <option>$value1</option>
    <option>$valueN</option>
  </propertyName>
  ```

## Maps

`XMap` annotation intended to customize map serialization and to enable new serialization format.

* With `XMap` annotation:
  ```xml
  <propertyName>
    <entry key="key1" value="value1" />
    <entry key="keyN" value="valueN" />
  </propertyName>
  ```
  
* Without `XMap` annotation:
  ```xml
  <option name="propertyName">
    <map>
      <entry key="key1" value="value1" />
      <entry key="keyN" value="valueN" />
    </map>
  </option>
  ```
  
So, it is recommended to always specify `XMap` annotation.
  