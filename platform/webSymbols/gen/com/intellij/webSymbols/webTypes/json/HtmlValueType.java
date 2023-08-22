
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import java.util.List;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

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
        extends JsonDeserializer<HtmlValueType>
    {


        @Override
        public HtmlValueType deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
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
                        result.value = parser.getCodec().readValue(parser, deserializationContext.getTypeFactory().constructParametricType(List.class, Type.class));
                    } else {
                        deserializationContext.handleUnexpectedToken(Object.class, parser);
                    }
                }
            }
            return result;
        }

    }

}
