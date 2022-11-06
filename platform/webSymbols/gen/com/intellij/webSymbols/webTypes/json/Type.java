
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

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
        extends JsonDeserializer<Type>
    {


        @Override
        public Type deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
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
