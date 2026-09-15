
package com.intellij.polySymbols.webTypes.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

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
        extends ValueDeserializer<Reference>
    {


        @Override
        public Reference deserialize(JsonParser parser, DeserializationContext deserializationContext)
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
