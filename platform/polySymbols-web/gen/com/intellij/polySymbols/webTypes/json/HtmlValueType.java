
package com.intellij.polySymbols.webTypes.json;

import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = HtmlValueType.MyDeserializer.class)
public class HtmlValueType {

    /**
     * Type: {@code String | TypeReference | List<Type>}
     * 
     */
    private Object value;

    /**
     * Type: {@code String | TypeReference | List<Type>}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code String | TypeReference | List<Type>}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends ValueDeserializer<HtmlValueType>
    {


        @Override
        public HtmlValueType deserialize(JsonParser parser, DeserializationContext deserializationContext)
        {
            HtmlValueType result = new HtmlValueType();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_STRING) {
                result.value = parser.readValueAs(String.class);
            } else {
                if (token == JsonToken.START_OBJECT) {
                    result.value = parser.readValueAs(TypeReference.class);
                } else {
                    if (token == JsonToken.START_ARRAY) {
                        result.value = deserializationContext.readValue(parser, deserializationContext.getTypeFactory().constructParametricType(List.class, Type.class));
                    } else {
                        deserializationContext.handleUnexpectedToken(Object.class, parser);
                    }
                }
            }
            return result;
        }

    }

}
