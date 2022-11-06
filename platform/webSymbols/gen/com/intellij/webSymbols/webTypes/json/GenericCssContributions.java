
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import java.util.ArrayList;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = GenericCssContributions.MyDeserializer.class)
public class GenericCssContributions
    extends ArrayList<GenericCssContributionOrProperty>
{


    public static class MyDeserializer
        extends JsonDeserializer<GenericCssContributions>
    {


        @Override
        public GenericCssContributions deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            GenericCssContributions result = new GenericCssContributions();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_ARRAY) {
                while (parser.nextToken()!= JsonToken.END_ARRAY) {
                    token = parser.currentToken();
                    if ((((((token == JsonToken.VALUE_NUMBER_INT)||(token == JsonToken.VALUE_NUMBER_FLOAT))||(token == JsonToken.VALUE_TRUE))||(token == JsonToken.VALUE_FALSE))||(token == JsonToken.VALUE_STRING))||(token == JsonToken.START_OBJECT)) {
                        result.add(parser.readValueAs(GenericCssContributionOrProperty.class));
                    } else {
                        parser.readValueAsTree();
                    }
                }
            } else {
                if ((((((token == JsonToken.VALUE_NUMBER_INT)||(token == JsonToken.VALUE_NUMBER_FLOAT))||(token == JsonToken.VALUE_TRUE))||(token == JsonToken.VALUE_FALSE))||(token == JsonToken.VALUE_STRING))||(token == JsonToken.START_OBJECT)) {
                    result.add(parser.readValueAs(GenericCssContributionOrProperty.class));
                } else {
                    parser.readValueAsTree();
                }
            }
            return result;
        }

    }

}
