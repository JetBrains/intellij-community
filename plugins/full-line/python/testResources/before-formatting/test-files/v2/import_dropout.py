import abc
import re
from random import Random
from typing import List, Type, Optional

from data_processing.datapoint import CodeDataPoint
from data_processing.file_extensions import LanguageIds, EXT2LANG
from data_processing.processors.abstract_processor import ProcessorBase
from data_processing.processors.empty_line_remover import EmptyLineRemoverBase


class ImportDropoutBase(ProcessorBase):
    __PY_IMPORT_REGEX = re.compile(r"^[\t ]*(from +.* +)?import +(\([\S\s]*?\)|(.*(\\\n))*.*)", re.MULTILINE)
    __JAVA_IMPORT_REGEX = re.compile(r"^[\t ]*import[\s]+[\w]+([\s]*\.[\s]*[\w]+)*[\s]*;$", re.MULTILINE)
    __KOTLIN_IMPORT_REGEX = re.compile(r"^[\t ]*import[\t ]+[\w]+([\t ]*\.[\t ]*[\w]+)*[\t ]*$", re.MULTILINE)
    __JS_TS_IMPORT_REGEX = re.compile(
        r"("
        # `const smth = require('smth')`, but not `const version = require('smth').version`
        r"[\t ]*(const|let)\s+\w+\s*=\s*require\s*\([^)]+?\);?(?=[^.])"
        r"|"
        # import Smth, {abc, bcd} from 'smth'
        r"[\t ]*import[\s|{][\w\s,{}]+[\s|}]from\s*(?P<quote>[\'\"]).*?(?P=quote);?"
        r")"
    )

    def __init__(self, dropout_rate: float):
        self._dropout_rate = dropout_rate
        self._rng = Random(0xBAD_C0DE)

    def _drop_imports(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        if EXT2LANG[dp.extension] == LanguageIds.PYTHON:
            pattern = self.__PY_IMPORT_REGEX
        elif EXT2LANG[dp.extension] == LanguageIds.JAVA:
            pattern = self.__JAVA_IMPORT_REGEX
        elif EXT2LANG[dp.extension] == LanguageIds.KOTLIN:
            pattern = self.__KOTLIN_IMPORT_REGEX
        elif EXT2LANG[dp.extension] == LanguageIds.JAVASCRIPT:
            pattern = self.__JS_TS_IMPORT_REGEX
        elif EXT2LANG[dp.extension] == LanguageIds.TYPESCRIPT:
            pattern = self.__JS_TS_IMPORT_REGEX
        else:
            return dp

        new_code = dp.content
        offset = 0
        for match in re.finditer(pattern, dp.content):
            start, end = match.span()
            if self._rng.random() < self._dropout_rate:
                new_beg = new_code[: start - offset]
                new_end = new_code[end - offset + 1 :]
                new_code = new_beg + new_end
                offset += (end + 1) - start
        if new_code.strip():
            dp.content = new_code
            return dp
        else:
            return None

    @abc.abstractmethod
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        raise NotImplementedError

    @staticmethod
    def is_deterministic() -> bool:
        return False

    @staticmethod
    def is_slow() -> bool:
        return False

    @staticmethod
    def must_be_after() -> List[Type[ProcessorBase]]:
        return []

    @staticmethod
    def must_be_before() -> List[Type[ProcessorBase]]:
        return [EmptyLineRemoverBase]


class ImportDropoutTrainData(ImportDropoutBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return dp


class ImportDropoutTrainModel(ImportDropoutBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return self._drop_imports(dp)


class ImportDropoutInference(ImportDropoutBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return dp
