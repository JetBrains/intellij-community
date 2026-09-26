
package com.intellij.polySymbols.webTypes.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = Type.MyDeserializer.class)
public class Type {

    /**
     * Type: {@code TypeReference | String}
     * 
     */
    private Object value;

    /**
     * Type: {@code TypeReference | String}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code TypeReference | String}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends ValueDeserializer<Type>
    {


        @Override
        public Type deserialize(JsonParser parser, DeserializationContext deserializationContext)
        {
            Type result = new Type();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_OBJECT) {
                result.value = parser.readValueAs(TypeReference.class);
            } else {
                if (token == JsonToken.VALUE_STRING) {
                    result.value = parser.readValueAs(String.class);
                } else {
                    deserializationContext.handleUnexpectedToken(Object.class, parser);
                }
            }
            return result;
        }

    }

}
