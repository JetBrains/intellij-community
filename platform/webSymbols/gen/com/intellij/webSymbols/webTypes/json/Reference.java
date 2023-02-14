
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = Reference.MyDeserializer.class)
public class Reference {

    /**
     * Type: {@code ReferenceWithProps | String}
     * 
     */
    private Object value;

    /**
     * Type: {@code ReferenceWithProps | String}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code ReferenceWithProps | String}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends JsonDeserializer<Reference>
    {


        @Override
        public Reference deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            Reference result = new Reference();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_OBJECT) {
                result.value = parser.readValueAs(ReferenceWithProps.class);
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
