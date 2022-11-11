from typing import List, Type, Optional
from data_processing.datapoint import CodeDataPoint
from data_processing.file_extensions import EXT2LANG
from data_processing.processors.abstract_processor import ProcessorBase
class LanguageFilterBase(ProcessorBase):⇥def __init__(self, language: str) -> None:⇥super().__init__()
self._language = language⇤def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return self._filter_file(dp)⇤def _filter_file(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return dp if self._language == "all" or EXT2LANG[dp.extension] == self._language else None⇤@staticmethod
def is_deterministic() -> bool:⇥return True⇤@staticmethod
def must_be_after() -> List[Type[ProcessorBase]]:⇥return []⇤@staticmethod
def must_be_before() -> List[Type[ProcessorBase]]:⇥return []⇤⇤class LanguageFilterTrainData(LanguageFilterBase):⇥pass⇤class LanguageFilterTrainModel(LanguageFilterBase):⇥pass⇤class LanguageFilterInference(LanguageFilterBase):⇥pass⇤